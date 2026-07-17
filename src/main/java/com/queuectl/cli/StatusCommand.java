package com.queuectl.cli;

import com.queuectl.domain.JobState;
import com.queuectl.domain.WorkerProcess;
import com.queuectl.service.JobService;
import com.queuectl.service.WorkerService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Component
@Command(name = "status", description = "Show a summary of all job states and active workers")
public class StatusCommand implements Callable<Integer> {

    private final JobService jobService;
    private final WorkerService workerService;

    public StatusCommand(JobService jobService, WorkerService workerService) {
        this.jobService = jobService;
        this.workerService = workerService;
    }

    @Override
    public Integer call() {
        Map<JobState, Long> counts = jobService.countByState();
        System.out.println("Job states:");
        for (JobState state : JobState.values()) {
            System.out.printf("  %-11s %d%n", state.name().toLowerCase() + ":", counts.getOrDefault(state, 0L));
        }

        List<WorkerProcess> workers = workerService.activeWorkers();
        System.out.println();
        System.out.println("Active workers: " + workers.size());
        if (!workers.isEmpty()) {
            System.out.printf("  %-36s %-8s %-8s %-10s %-24s%n", "ID", "PID", "THREADS", "STATUS", "LAST_HEARTBEAT");
            for (WorkerProcess wp : workers) {
                System.out.printf("  %-36s %-8d %-8d %-10s %-24s%n",
                        wp.getId(), wp.getPid(), wp.getThreadCount(), wp.getStatus(), wp.getLastHeartbeat());
            }
        }
        return 0;
    }
}
