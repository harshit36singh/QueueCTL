package com.queuectl.cli;

import com.queuectl.domain.WorkerStatus;
import com.queuectl.service.WorkerService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Component
@Command(name = "stop", description = "Gracefully stop running worker processes")
public class WorkerStopCommand implements Callable<Integer> {

    @Option(names = "--id", description = "Specific worker process id to stop; if omitted, stops all running worker processes")
    private String id;

    @Option(names = "--wait-seconds", defaultValue = "30", description = "How long to wait for confirmation of a clean stop (default: 30)")
    private int waitSeconds;

    private final WorkerService workerService;

    public WorkerStopCommand(WorkerService workerService) {
        this.workerService = workerService;
    }

    @Override
    public Integer call() throws InterruptedException {
        int signalled = workerService.requestStop(id);
        if (signalled == 0) {
            System.out.println("No running worker process(es) found" + (id != null ? " with id " + id : "") + ".");
            return 0;
        }
        System.out.println("Stop requested for " + signalled + " worker process(es). Waiting for graceful shutdown...");

        long deadline = System.currentTimeMillis() + waitSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (isFullyStopped()) {
                System.out.println("All targeted worker processes have stopped.");
                return 0;
            }
            Thread.sleep(1000);
        }
        System.out.println("Timed out after " + waitSeconds + "s waiting for confirmation; "
                + "worker(s) may still be finishing an in-flight job. Check 'queuectl status'.");
        return 0;
    }

    private boolean isFullyStopped() {
        if (id != null) {
            return workerService.find(id).map(wp -> wp.getStatus() == WorkerStatus.STOPPED).orElse(true);
        }
        return workerService.activeWorkers().isEmpty();
    }
}
