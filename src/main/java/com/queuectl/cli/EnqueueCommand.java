package com.queuectl.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuectl.domain.Job;
import com.queuectl.dto.EnqueueRequest;
import com.queuectl.service.JobService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Callable;

@Component
@Command(name = "enqueue", description = "Add a new job to the queue")
public class EnqueueCommand implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", paramLabel = "JSON",
            description = "Job definition as JSON, e.g. '{\"id\":\"job1\",\"command\":\"sleep 2\"}'. "
                    + "Omit (or pass '-') to read the JSON from stdin instead -- useful on shells "
                    + "(e.g. Windows PowerShell) where quoting embedded double-quotes as a single "
                    + "argument is unreliable.")
    private String json;

    private final JobService jobService;
    private final ObjectMapper objectMapper;

    public EnqueueCommand(JobService jobService, ObjectMapper objectMapper) {
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Integer call() {
        String payload = (json == null || "-".equals(json)) ? readStdin() : json;

        EnqueueRequest request;
        try {
            request = objectMapper.readValue(payload, EnqueueRequest.class);
        } catch (JsonProcessingException e) {
            System.err.println("Invalid JSON: " + e.getOriginalMessage());
            return 1;
        }

        Instant runAt = null;
        if (request.getRunAt() != null && !request.getRunAt().isBlank()) {
            try {
                runAt = Instant.parse(request.getRunAt());
            } catch (DateTimeParseException e) {
                System.err.println("Invalid run_at (expected ISO-8601, e.g. 2025-11-04T10:30:00Z): " + request.getRunAt());
                return 1;
            }
        }

        try {
            Job job = jobService.enqueue(
                    request.getId(),
                    request.getCommand(),
                    request.getMaxRetries(),
                    request.getBackoffBase(),
                    request.getPriority(),
                    runAt);
            System.out.println("Enqueued job:");
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(JobPrinter.toJsonMap(job)));
            return 0;
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        } catch (JsonProcessingException e) {
            // Should be unreachable: we're serializing our own well-formed map.
            System.err.println("Error formatting output: " + e.getOriginalMessage());
            return 1;
        }
    }

    private static String readStdin() {
        try {
            String text = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            // PowerShell's pipe writes a UTF-8 BOM (U+FEFF) ahead of piped string content on Windows.
            return (!text.isEmpty() && text.charAt(0) == '﻿') ? text.substring(1) : text;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
