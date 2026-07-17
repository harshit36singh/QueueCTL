package com.queuectl.web;

import com.queuectl.domain.WorkerProcess;

import java.time.Instant;

public record WorkerResponse(
        String id,
        long pid,
        int threadCount,
        String status,
        Instant startedAt,
        Instant lastHeartbeat
) {
    public static WorkerResponse from(WorkerProcess wp) {
        return new WorkerResponse(
                wp.getId(),
                wp.getPid(),
                wp.getThreadCount(),
                wp.getStatus().name().toLowerCase(),
                wp.getStartedAt(),
                wp.getLastHeartbeat());
    }
}
