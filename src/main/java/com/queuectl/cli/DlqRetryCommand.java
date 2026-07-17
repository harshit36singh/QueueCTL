package com.queuectl.cli;

import com.queuectl.domain.Job;
import com.queuectl.service.JobService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.NoSuchElementException;
import java.util.concurrent.Callable;

@Component
@Command(name = "retry", description = "Move a job from the DLQ back to pending, resetting its attempt count")
public class DlqRetryCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "JOB_ID", description = "Id of the dead-lettered job to retry")
    private String jobId;

    private final JobService jobService;

    public DlqRetryCommand(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public Integer call() {
        try {
            Job job = jobService.dlqRetry(jobId);
            System.out.println("Job '" + job.getId() + "' re-queued (state=pending, attempts reset to 0).");
            return 0;
        } catch (NoSuchElementException | IllegalStateException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }
}
