package com.queuectl.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuectl.domain.Job;
import com.queuectl.dto.EnqueueRequest;
import com.queuectl.service.JobService;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.concurrent.Callable;

@Component
@Command(name = "enqueue", description = "Add a new job to the queue")
public class EnqueueCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "JSON",
            description = "Job definition as JSON, e.g. '{\"id\":\"job1\",\"command\":\"sleep 2\"}'")
    private String json;

    private final JobService jobService;
    private final ObjectMapper objectMapper;

    public EnqueueCommand(JobService jobService, ObjectMapper objectMapper) {
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Integer call() {
        EnqueueRequest request;
        try {
            request = objectMapper.readValue(json, EnqueueRequest.class);
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
}
