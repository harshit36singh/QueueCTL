package com.queuectl.cli;

import com.queuectl.service.WorkerService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Component
@Command(name = "start", description = "Start worker threads that claim and execute jobs (runs in the foreground until stopped)")
public class WorkerStartCommand implements Callable<Integer> {

    @Option(names = "--count", defaultValue = "1", description = "Number of worker threads to run (default: 1)")
    private int count;

    private final WorkerService workerService;

    public WorkerStartCommand(WorkerService workerService) {
        this.workerService = workerService;
    }

    @Override
    public Integer call() {
        if (count < 1) {
            System.err.println("--count must be >= 1");
            return 1;
        }
        return workerService.runForeground(count);
    }
}
