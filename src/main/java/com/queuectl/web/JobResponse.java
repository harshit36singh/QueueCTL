package com.queuectl.web;

import com.queuectl.domain.Job;

import java.time.Instant;

public record JobResponse(
        String id,
        String command,
        String state,
        int attempts,
        int maxRetries,
        int backoffBase,
        int priority,
        Instant runAt,
        Instant nextRunAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getId(),
                job.getCommand(),
                job.getState().name().toLowerCase(),
                job.getAttempts(),
                job.getMaxRetries(),
                job.getBackoffBase(),
                job.getPriority(),
                job.getRunAt(),
                job.getNextRunAt(),
                job.getLastError(),
                job.getCreatedAt(),
                job.getUpdatedAt());
    }
}
