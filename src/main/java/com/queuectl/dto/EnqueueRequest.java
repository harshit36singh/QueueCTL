package com.queuectl.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors the enqueue JSON payload. Fields the server manages itself (state, attempts,
 * created_at, updated_at) are accepted and ignored so a full job JSON can be pasted back in.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnqueueRequest {

    private String id;
    private String command;

    @JsonProperty("max_retries")
    private Integer maxRetries;

    @JsonProperty("backoff_base")
    private Integer backoffBase;

    private Integer priority;

    @JsonProperty("run_at")
    private String runAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Integer getBackoffBase() {
        return backoffBase;
    }

    public void setBackoffBase(Integer backoffBase) {
        this.backoffBase = backoffBase;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getRunAt() {
        return runAt;
    }

    public void setRunAt(String runAt) {
        this.runAt = runAt;
    }
}
