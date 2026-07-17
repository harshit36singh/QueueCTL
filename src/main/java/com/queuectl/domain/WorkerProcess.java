package com.queuectl.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "worker_processes")
public class WorkerProcess {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "pid", nullable = false)
    private long pid;

    @Column(name = "thread_count", nullable = false)
    private int threadCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private WorkerStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_heartbeat", nullable = false)
    private Instant lastHeartbeat;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    protected WorkerProcess() {
    }

    public WorkerProcess(String id, long pid, int threadCount) {
        this.id = id;
        this.pid = pid;
        this.threadCount = threadCount;
        this.status = WorkerStatus.RUNNING;
        Instant now = Instant.now();
        this.startedAt = now;
        this.lastHeartbeat = now;
    }

    public String getId() {
        return id;
    }

    public long getPid() {
        return pid;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public WorkerStatus getStatus() {
        return status;
    }

    public void setStatus(WorkerStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public Instant getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(Instant stoppedAt) {
        this.stoppedAt = stoppedAt;
    }
}
