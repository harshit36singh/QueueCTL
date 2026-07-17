package com.queuectl.cli;

import com.queuectl.service.ConfigService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

@Component
@Command(name = "set", description = "Set a configuration value, e.g. 'config set max-retries 5'")
public class ConfigSetCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "KEY",
            description = "One of: max-retries, backoff-base, poll-interval-ms, job-timeout-seconds, stale-processing-seconds, heartbeat-interval-ms")
    private String key;

    @Parameters(index = "1", paramLabel = "VALUE", description = "Integer value")
    private String value;

    private final ConfigService configService;

    public ConfigSetCommand(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public Integer call() {
        try {
            configService.set(key, value);
            System.out.println(key + " = " + value);
            return 0;
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }
}
