package com.queuectl.cli;

import com.queuectl.domain.Job;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JobPrinter {

    private JobPrinter() {
    }

    static Map<String, Object> toJsonMap(Job job) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", job.getId());
        map.put("command", job.getCommand());
        map.put("state", job.getState().name().toLowerCase());
        map.put("attempts", job.getAttempts());
        map.put("max_retries", job.getMaxRetries());
        map.put("backoff_base", job.getBackoffBase());
        map.put("priority", job.getPriority());
        if (job.getRunAt() != null) {
            map.put("run_at", job.getRunAt().toString());
        }
        if (job.getNextRunAt() != null) {
            map.put("next_run_at", job.getNextRunAt().toString());
        }
        if (job.getLastError() != null) {
            map.put("last_error", job.getLastError());
        }
        map.put("created_at", job.getCreatedAt().toString());
        map.put("updated_at", job.getUpdatedAt().toString());
        return map;
    }

    static void printTable(List<Job> jobs) {
        if (jobs.isEmpty()) {
            System.out.println("No jobs found.");
            return;
        }
        System.out.printf("%-36s %-11s %-9s %-4s %-24s %-40s%n", "ID", "STATE", "ATTEMPTS", "MAX", "UPDATED_AT", "COMMAND");
        for (Job job : jobs) {
            String cmd = job.getCommand();
            if (cmd.length() > 40) {
                cmd = cmd.substring(0, 37) + "...";
            }
            System.out.printf("%-36s %-11s %-9d %-4d %-24s %-40s%n",
                    job.getId(),
                    job.getState().name().toLowerCase(),
                    job.getAttempts(),
                    job.getMaxRetries(),
                    job.getUpdatedAt(),
                    cmd);
        }
    }
}
