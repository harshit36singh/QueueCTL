package com.queuectl.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Component
@Command(name = "dashboard", description = "Start the read-only web dashboard for monitoring jobs and workers",
        subcommands = {DashboardStartCommand.class})
public class DashboardCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }
}
