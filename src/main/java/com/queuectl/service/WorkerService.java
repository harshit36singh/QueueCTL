package com.queuectl.service;

import com.queuectl.domain.Job;
import com.queuectl.domain.WorkerProcess;
import com.queuectl.domain.WorkerStatus;
import com.queuectl.repository.WorkerProcessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts and gracefully stops the worker threads that claim and execute jobs.
 *
 * <p>Each {@code worker start} invocation is its own OS process (its own JVM). Workers within
 * that process are plain threads sharing a fixed pool. Because job claiming uses a
 * {@code SELECT ... FOR UPDATE SKIP LOCKED} query against MySQL, this also stays correct if
 * multiple separate {@code worker start} processes are run concurrently against the same
 * database, not just multiple threads in one process.
 *
 * <p>{@code worker stop} runs as a *separate* CLI invocation, so it cannot signal this process
 * directly. Instead it flips this process's row in {@code worker_processes} to STOPPING; a
 * heartbeat thread here polls that row and, on seeing STOPPING, flips a local flag that worker
 * threads check between jobs (never mid-job) before exiting.
 */
@Service
public class WorkerService {

    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private final JobService jobService;
    private final JobExecutionService jobExecutionService;
    private final ConfigService configService;
    private final WorkerProcessRepository workerProcessRepository;

    public WorkerService(JobService jobService,
                          JobExecutionService jobExecutionService,
                          ConfigService configService,
                          WorkerProcessRepository workerProcessRepository) {
        this.jobService = jobService;
        this.jobExecutionService = jobExecutionService;
        this.configService = configService;
        this.workerProcessRepository = workerProcessRepository;
    }

    /**
     * Runs {@code threadCount} worker threads in the foreground until a stop is requested
     * (via {@code worker stop} in another process, or Ctrl+C / SIGTERM in this one).
     * Blocks the calling thread until shutdown is complete.
     */
    public int runForeground(int threadCount) {
        String processId = UUID.randomUUID().toString();
        long pid = ProcessHandle.current().pid();
        registerProcess(processId, pid, threadCount);

        AtomicBoolean stopRequested = new AtomicBoolean(false);
        AtomicBoolean stoppedMarked = new AtomicBoolean(false);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        System.out.printf("Worker process %s started (pid=%d, threads=%d)%n", processId, pid, threadCount);
        System.out.println("Run 'queuectl worker stop' (or Ctrl+C) to shut down gracefully.");

        for (int i = 0; i < threadCount; i++) {
            String workerId = processId + "-" + i;
            futures.add(pool.submit(() -> workerLoop(workerId, stopRequested)));
        }

        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "queuectl-heartbeat");
            t.setDaemon(true);
            return t;
        });
        long heartbeatMs = configService.getHeartbeatIntervalMs();
        heartbeat.scheduleAtFixedRate(() -> {
            try {
                if (isStopRequestedInDb(processId)) {
                    stopRequested.set(true);
                }
                touchHeartbeat(processId);
            } catch (Exception e) {
                log.warn("heartbeat update failed: {}", e.getMessage());
            }
        }, 0, heartbeatMs, TimeUnit.MILLISECONDS);

        long shutdownJoinTimeoutSeconds = configService.getJobTimeoutSeconds() + 10;
        Thread shutdownHook = new Thread(() -> {
            System.out.println("\nShutdown signal received, finishing in-flight jobs before exit...");
            stopRequested.set(true);
            awaitFutures(futures, shutdownJoinTimeoutSeconds);
            if (stoppedMarked.compareAndSet(false, true)) {
                markStopped(processId);
            }
            System.out.println("Worker process " + processId + " stopped.");
        }, "queuectl-shutdown-hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        awaitFutures(futures, Long.MAX_VALUE);
        heartbeat.shutdownNow();
        pool.shutdown();
        if (stoppedMarked.compareAndSet(false, true)) {
            markStopped(processId);
        }
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down (Ctrl+C path); the hook itself will finish the job.
        }
        System.out.println("Worker process " + processId + " stopped.");
        return 0;
    }

    private void awaitFutures(List<Future<?>> futures, long timeoutSeconds) {
        long deadline = (timeoutSeconds == Long.MAX_VALUE)
                ? Long.MAX_VALUE
                : System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        for (Future<?> future : futures) {
            try {
                if (deadline == Long.MAX_VALUE) {
                    future.get();
                } else {
                    long remaining = Math.max(0, deadline - System.currentTimeMillis());
                    future.get(remaining, TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                log.warn("worker thread did not finish cleanly: {}", e.getMessage());
            }
        }
    }

    private void workerLoop(String workerId, AtomicBoolean stopRequested) {
        long pollMs = configService.getPollIntervalMs();
        while (!stopRequested.get()) {
            Optional<Job> jobOpt;
            try {
                jobOpt = jobService.claimNextJob(workerId);
            } catch (Exception e) {
                log.warn("[{}] failed to claim next job: {}", workerId, e.getMessage());
                sleep(pollMs);
                continue;
            }
            if (jobOpt.isPresent()) {
                jobExecutionService.execute(jobOpt.get(), workerId);
            } else {
                sleep(pollMs);
            }
        }
        log.info("[{}] worker thread exiting", workerId);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Note: these helpers are called via self-invocation (this.xxx()) from within this same
    // class, which bypasses Spring's proxy-based AOP, so @Transactional here would be a no-op.
    // Each call below still commits atomically because Spring Data's SimpleJpaRepository
    // already wraps every individual findById()/save() call in its own transaction.

    private void registerProcess(String id, long pid, int threadCount) {
        workerProcessRepository.save(new WorkerProcess(id, pid, threadCount));
    }

    private void touchHeartbeat(String id) {
        workerProcessRepository.findById(id).ifPresent(wp -> {
            wp.setLastHeartbeat(Instant.now());
            workerProcessRepository.save(wp);
        });
    }

    private boolean isStopRequestedInDb(String id) {
        return workerProcessRepository.findById(id)
                .map(wp -> wp.getStatus() == WorkerStatus.STOPPING)
                .orElse(false);
    }

    private void markStopped(String id) {
        workerProcessRepository.findById(id).ifPresent(wp -> {
            wp.setStatus(WorkerStatus.STOPPED);
            wp.setStoppedAt(Instant.now());
            workerProcessRepository.save(wp);
        });
    }

    /**
     * Requests graceful shutdown of the given worker process, or all currently running
     * processes if {@code id} is null. Returns how many processes were signalled.
     */
    @Transactional
    public int requestStop(String id) {
        List<WorkerProcess> targets = (id != null)
                ? workerProcessRepository.findById(id).map(List::of).orElse(List.of())
                : workerProcessRepository.findByStatusIn(List.of(WorkerStatus.RUNNING));
        for (WorkerProcess wp : targets) {
            wp.setStatus(WorkerStatus.STOPPING);
            workerProcessRepository.save(wp);
        }
        return targets.size();
    }

    @Transactional(readOnly = true)
    public List<WorkerProcess> activeWorkers() {
        return workerProcessRepository.findByStatusIn(List.of(WorkerStatus.RUNNING, WorkerStatus.STOPPING));
    }

    @Transactional(readOnly = true)
    public Optional<WorkerProcess> find(String id) {
        return workerProcessRepository.findById(id);
    }
}
