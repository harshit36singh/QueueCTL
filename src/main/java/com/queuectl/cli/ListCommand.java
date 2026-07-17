package com.queuectl.cli;

import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;
import com.queuectl.service.JobService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

@Component
@Command(name = "list", description = "List jobs, optionally filtered by state")
public class ListCommand implements Callable<Integer> {

    @Option(names = "--state", description = "Filter by state: pending, processing, completed, failed, dead")
    private String state;

    @Option(names = "--limit", defaultValue = "20", description = "Maximum rows to show (default: 20)")
    private int limit;

    private final JobService jobService;

    public ListCommand(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public Integer call() {
        JobState jobState = null;
        if (state != null) {
            try {
                jobState = JobState.valueOf(state.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("Unknown state '" + state + "'. Valid values: pending, processing, completed, failed, dead");
                return 1;
            }
        }
        List<Job> jobs = jobService.list(jobState, limit);
        JobPrinter.printTable(jobs);
        return 0;
    }
}
