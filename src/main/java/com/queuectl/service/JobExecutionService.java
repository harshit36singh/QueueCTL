package com.queuectl.service;

import com.queuectl.domain.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Runs a job's shell command in a child process, capturing its output and exit code.
 */
@Service
public class JobExecutionService {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionService.class);
    private static final boolean WINDOWS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT).contains("win");

    private final JobService jobService;
    private final ConfigService configService;
    private final Path logDir;

    public JobExecutionService(JobService jobService, ConfigService configService) {
        this.jobService = jobService;
        this.configService = configService;
        this.logDir = Path.of("logs");
        try {
            Files.createDirectories(logDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create log directory " + logDir, e);
        }
    }

    public void execute(Job job, String workerId) {
        String jobId = job.getId();
        int attemptNumber = job.getAttempts() + 1;
        Path logFile = logDir.resolve(jobId + "-attempt-" + attemptNumber + ".log");
        log.info("[{}] running job {} (attempt {}/{}): {}", workerId, jobId, attemptNumber, job.getMaxRetries(), job.getCommand());

        try {
            List<String> commandLine = WINDOWS
                    ? List.of("cmd.exe", "/c", job.getCommand())
                    : List.of("sh", "-c", job.getCommand());

            ProcessBuilder builder = new ProcessBuilder(commandLine);
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()));

            Process process = builder.start();
            long timeoutSeconds = configService.getJobTimeoutSeconds();
            boolean finished = timeoutSeconds <= 0 || process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                log.warn("[{}] job {} timed out after {}s", workerId, jobId, timeoutSeconds);
                jobService.markFailure(jobId, "Timed out after " + timeoutSeconds + "s (output: " + logFile + ")");
                return;
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("[{}] job {} completed successfully", workerId, jobId);
                jobService.markSuccess(jobId);
            } else {
                log.warn("[{}] job {} failed with exit code {}", workerId, jobId, exitCode);
                jobService.markFailure(jobId, "Command exited with code " + exitCode + " (output: " + logFile + ")");
            }
        } catch (IOException e) {
            log.warn("[{}] job {} could not be started: {}", workerId, jobId, e.getMessage());
            jobService.markFailure(jobId, "Failed to start command: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[{}] job {} execution interrupted", workerId, jobId);
            jobService.markFailure(jobId, "Execution interrupted");
        }
    }
}
