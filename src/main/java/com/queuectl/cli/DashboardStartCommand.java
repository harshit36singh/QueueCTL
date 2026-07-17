package com.queuectl.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

@Component
@Command(name = "start", description = "Start the web dashboard (runs in the foreground until Ctrl+C)")
public class DashboardStartCommand implements Callable<Integer> {

    // Parsed here for --help/usage and validation; the actual port binding already happened
    // in QueueCtlApplication.main() before the Spring context (and its embedded server) came up.
    @Option(names = "--port", defaultValue = "8080", description = "HTTP port to listen on (default: 8080)")
    private int port;

    @Override
    public Integer call() throws InterruptedException {
        System.out.println("Dashboard listening on http://localhost:" + port);
        System.out.println("Press Ctrl+C to stop.");

        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(latch::countDown, "queuectl-dashboard-shutdown-hook"));
        latch.await();

        System.out.println("Dashboard stopped.");
        return 0;
    }
}
