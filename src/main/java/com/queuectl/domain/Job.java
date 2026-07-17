package com.queuectl.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Lob
    @Column(name = "command", nullable = false)
    private String command;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 16, nullable = false)
    private JobState state;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "backoff_base", nullable = false)
    private int backoffBase;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "run_at")
    private Instant runAt;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "locked_by", length = 64)
    private String lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Lob
    @Column(name = "last_error")
    private String lastError;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Job() {
    }

    public Job(String id, String command, int maxRetries, int backoffBase, int priority, Instant runAt) {
        this.id = id;
        this.command = command;
        this.state = JobState.PENDING;
        this.attempts = 0;
        this.maxRetries = maxRetries;
        this.backoffBase = backoffBase;
        this.priority = priority;
        this.runAt = runAt;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getCommand() {
        return command;
    }

    public JobState getState() {
        return state;
    }

    public void setState(JobState state) {
        this.state = state;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public int getBackoffBase() {
        return backoffBase;
    }

    public int getPriority() {
        return priority;
    }

    public Instant getRunAt() {
        return runAt;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(Instant nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(String lockedBy) {
        this.lockedBy = lockedBy;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(Instant lockedAt) {
        this.lockedAt = lockedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
