package com.queuectl.service;

import com.queuectl.domain.ConfigEntry;
import com.queuectl.repository.ConfigEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class ConfigService {

    public static final Set<String> KNOWN_KEYS = Set.of(
            "max-retries",
            "backoff-base",
            "poll-interval-ms",
            "job-timeout-seconds",
            "stale-processing-seconds",
            "heartbeat-interval-ms"
    );

    private final ConfigEntryRepository repository;

    public ConfigService(ConfigEntryRepository repository) {
        this.repository = repository;
    }

    public String get(String key, String defaultValue) {
        return repository.findById(key).map(ConfigEntry::getValue).orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return repository.findById(key)
                .map(entry -> Integer.parseInt(entry.getValue()))
                .orElse(defaultValue);
    }

    @Transactional
    public void set(String key, String value) {
        if (!KNOWN_KEYS.contains(key)) {
            throw new IllegalArgumentException("Unknown config key '" + key + "'. Known keys: " + KNOWN_KEYS);
        }
        try {
            Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Config value for '" + key + "' must be an integer, got '" + value + "'");
        }
        ConfigEntry entry = repository.findById(key).orElse(null);
        if (entry == null) {
            repository.save(new ConfigEntry(key, value));
        } else {
            entry.setValue(value);
            repository.save(entry);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, String> getAll() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : KNOWN_KEYS) {
            result.put(key, get(key, ""));
        }
        return result;
    }

    public int getMaxRetries() {
        return getInt("max-retries", 3);
    }

    public int getBackoffBase() {
        return getInt("backoff-base", 2);
    }

    public long getPollIntervalMs() {
        return getInt("poll-interval-ms", 1000);
    }

    public long getJobTimeoutSeconds() {
        return getInt("job-timeout-seconds", 300);
    }

    public long getStaleProcessingSeconds() {
        return getInt("stale-processing-seconds", 120);
    }

    public long getHeartbeatIntervalMs() {
        return getInt("heartbeat-interval-ms", 5000);
    }
}
