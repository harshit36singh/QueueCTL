package com.queuectl.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Component
@Command(name = "config", description = "Manage configuration (retry count, backoff base, timeouts, etc.)",
        subcommands = {ConfigSetCommand.class, ConfigGetCommand.class})
public class ConfigCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }
}
