package com.queuectl.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Component
@Command(name = "dlq", description = "View or retry jobs in the dead letter queue",
        subcommands = {DlqListCommand.class, DlqRetryCommand.class})
public class DlqCommand implements Runnable {

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }
}
