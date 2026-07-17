package com.queuectl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.queuectl.cli.RootCommand;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import picocli.CommandLine;

@SpringBootApplication
public class QueueCtlApplication implements CommandLineRunner, ExitCodeGenerator {

    private final RootCommand rootCommand;
    private final CommandLine.IFactory factory;
    private int exitCode;

    public QueueCtlApplication(RootCommand rootCommand, CommandLine.IFactory factory) {
        this.rootCommand = rootCommand;
        this.factory = factory;
    }

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(
                new SpringApplicationBuilder(QueueCtlApplication.class)
                        .web(WebApplicationType.NONE)
                        .bannerMode(Banner.Mode.OFF)
                        .logStartupInfo(false)
                        .run(args)));
    }

    @Override
    public void run(String... args) {
        exitCode = new CommandLine(rootCommand, factory).execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
