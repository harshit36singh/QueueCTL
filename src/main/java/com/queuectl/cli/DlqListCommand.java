package com.queuectl.cli;

import com.queuectl.service.JobService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Component
@Command(name = "list", description = "List jobs in the dead letter queue")
public class DlqListCommand implements Callable<Integer> {

    @Option(names = "--limit", defaultValue = "20", description = "Maximum rows to show (default: 20)")
    private int limit;

    private final JobService jobService;

    public DlqListCommand(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public Integer call() {
        JobPrinter.printTable(jobService.dlqList(limit));
        return 0;
    }
}
