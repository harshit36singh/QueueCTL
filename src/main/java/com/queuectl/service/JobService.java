package com.queuectl.service;

import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;
import com.queuectl.repository.JobRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobService {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final JobRepository jobRepository;
    private final ConfigService configService;

    public JobService(JobRepository jobRepository, ConfigService configService) {
        this.jobRepository = jobRepository;
        this.configService = configService;
    }

    @Transactional
    public Job enqueue(String id, String command, Integer maxRetries, Integer backoffBase, Integer priority, Instant runAt) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("'command' is required");
        }
        String jobId = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id.trim();
        if (jobRepository.existsById(jobId)) {
            throw new IllegalArgumentException("Job with id '" + jobId + "' already exists");
        }
        int resolvedMaxRetries = (maxRetries != null) ? maxRetries : configService.getMaxRetries();
        int resolvedBackoffBase = (backoffBase != null) ? backoffBase : configService.getBackoffBase();
        int resolvedPriority = (priority != null) ? priority : 0;
        if (resolvedMaxRetries < 0) {
            throw new IllegalArgumentException("'max_retries' must be >= 0");
        }
        if (resolvedBackoffBase < 1) {
            throw new IllegalArgumentException("'backoff_base' must be >= 1");
        }

        Job job = new Job(jobId, command, resolvedMaxRetries, resolvedBackoffBase, resolvedPriority, runAt);
        return jobRepository.save(job);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<Job> claimNextJob(String workerId) {
        Instant staleThreshold = Instant.now().minusSeconds(configService.getStaleProcessingSeconds());
        return jobRepository.findNextClaimableId(staleThreshold).map(id -> {
            Job job = jobRepository.findById(id).orElseThrow();
            Instant now = Instant.now();
            job.setState(JobState.PROCESSING);
            job.setLockedBy(workerId);
            job.setLockedAt(now);
            job.setStartedAt(now);
            job.setUpdatedAt(now);
            return jobRepository.save(job);
        });
    }

    @Transactional
    public void markSuccess(String jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        job.setState(JobState.COMPLETED);
        job.setCompletedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        job.setLockedBy(null);
        job.setLockedAt(null);
        job.setLastError(null);
        jobRepository.save(job);
    }

    @Transactional
    public void markFailure(String jobId, String error) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        int attempts = job.getAttempts() + 1;
        job.setAttempts(attempts);
        job.setLastError(truncate(error, MAX_ERROR_LENGTH));
        job.setUpdatedAt(Instant.now());
        job.setLockedBy(null);
        job.setLockedAt(null);
        if (attempts >= job.getMaxRetries()) {
            job.setState(JobState.DEAD);
            job.setNextRunAt(null);
        } else {
            job.setState(JobState.FAILED);
            long delaySeconds = BackoffCalculator.delaySeconds(job.getBackoffBase(), attempts);
            job.setNextRunAt(Instant.now().plusSeconds(delaySeconds));
        }
        jobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public List<Job> list(JobState state, int limit) {
        int safeLimit = Math.max(1, limit);
        if (state == null) {
            return jobRepository.findAll(PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
        }
        return jobRepository.findByStateOrderByCreatedAtDesc(state, PageRequest.of(0, safeLimit));
    }

    @Transactional(readOnly = true)
    public Map<JobState, Long> countByState() {
        Map<JobState, Long> counts = new EnumMap<>(JobState.class);
        for (JobState state : JobState.values()) {
            counts.put(state, 0L);
        }
        for (JobRepository.StateCount row : jobRepository.countGroupedByState()) {
            counts.put(row.getState(), row.getTotal());
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public List<Job> dlqList(int limit) {
        return list(JobState.DEAD, limit);
    }

    @Transactional
    public Job dlqRetry(String jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Job '" + jobId + "' not found"));
        if (job.getState() != JobState.DEAD) {
            throw new IllegalStateException("Job '" + jobId + "' is not in the DLQ (current state: " + job.getState() + ")");
        }
        job.setState(JobState.PENDING);
        job.setAttempts(0);
        job.setNextRunAt(null);
        job.setLastError(null);
        job.setLockedBy(null);
        job.setLockedAt(null);
        job.setUpdatedAt(Instant.now());
        return jobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public Optional<Job> find(String jobId) {
        return jobRepository.findById(jobId);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
