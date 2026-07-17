package com.queuectl.cli;

import com.queuectl.service.ConfigService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

@Component
@Command(name = "get", description = "Show configuration values (all, or a single key)")
public class ConfigGetCommand implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", paramLabel = "KEY", description = "Specific key to show; omit to show all")
    private String key;

    private final ConfigService configService;

    public ConfigGetCommand(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public Integer call() {
        if (key != null) {
            if (!ConfigService.KNOWN_KEYS.contains(key)) {
                System.err.println("Unknown config key '" + key + "'. Known keys: " + ConfigService.KNOWN_KEYS);
                return 1;
            }
            System.out.println(key + " = " + configService.get(key, ""));
            return 0;
        }
        configService.getAll().forEach((k, v) -> System.out.println(k + " = " + v));
        return 0;
    }
}
