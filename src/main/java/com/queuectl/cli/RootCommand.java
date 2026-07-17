package com.queuectl.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Component
@Command(
        name = "queuectl",
        mixinStandardHelpOptions = true,
        version = "queuectl 1.0.0",
        description = "CLI-based background job queue with worker processes, retries with exponential backoff, and a dead letter queue.",
        subcommands = {
                EnqueueCommand.class,
                WorkerCommand.class,
                StatusCommand.class,
                ListCommand.class,
                DlqCommand.class,
                ConfigCommand.class,
                DashboardCommand.class,
                picocli.CommandLine.HelpCommand.class
        }
)
public class RootCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }
}
