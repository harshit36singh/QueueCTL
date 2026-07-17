package com.queuectl.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Component
@Command(name = "worker", description = "Start or stop worker processes",
        subcommands = {WorkerStartCommand.class, WorkerStopCommand.class})
public class WorkerCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }
}
