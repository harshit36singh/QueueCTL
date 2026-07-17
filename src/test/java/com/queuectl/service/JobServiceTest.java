package com.queuectl.service;

import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;
import com.queuectl.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private ConfigService configService;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobRepository, configService);
    }

    @Test
    void enqueueRejectsBlankCommand() {
        assertThatThrownBy(() -> jobService.enqueue("job1", "  ", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command");
    }

    @Test
    void enqueueGeneratesIdWhenMissing() {
        when(jobRepository.existsById(anyString())).thenReturn(false);
        when(configService.getMaxRetries()).thenReturn(3);
        when(configService.getBackoffBase()).thenReturn(2);
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Job job = jobService.enqueue(null, "echo hi", null, null, null, null);

        assertThat(job.getId()).isNotBlank();
        assertThat(job.getState()).isEqualTo(JobState.PENDING);
        assertThat(job.getMaxRetries()).isEqualTo(3);
        assertThat(job.getBackoffBase()).isEqualTo(2);
    }

    @Test
    void enqueueRejectsDuplicateId() {
        when(jobRepository.existsById("job1")).thenReturn(true);

        assertThatThrownBy(() -> jobService.enqueue("job1", "echo hi", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void enqueueHonoursPerJobMaxRetriesOverride() {
        when(jobRepository.existsById(anyString())).thenReturn(false);
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Job job = jobService.enqueue("job1", "echo hi", 7, 5, null, null);

        assertThat(job.getMaxRetries()).isEqualTo(7);
        assertThat(job.getBackoffBase()).isEqualTo(5);
    }

    @Test
    void markFailureRetriesWithBackoffWhenAttemptsRemain() {
        Job job = new Job("job1", "false", 3, 2, 0, null);
        job.setState(JobState.PROCESSING);
        when(jobRepository.findById("job1")).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        jobService.markFailure("job1", "boom");

        assertThat(job.getState()).isEqualTo(JobState.FAILED);
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(job.getLastError()).isEqualTo("boom");
        assertThat(job.getNextRunAt()).isAfter(Instant.now());
        // base=2, attempts=1 -> delay=2s, so next_run_at should land within a generous window
        assertThat(job.getNextRunAt()).isBeforeOrEqualTo(Instant.now().plusSeconds(5));
    }

    @Test
    void markFailureMovesToDeadLetterQueueOnceRetriesExhausted() {
        Job job = new Job("job1", "false", 3, 2, 0, null);
        job.setState(JobState.PROCESSING);
        job.setAttempts(2); // this failure will be the 3rd attempt == max_retries
        when(jobRepository.findById("job1")).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        jobService.markFailure("job1", "still broken");

        assertThat(job.getState()).isEqualTo(JobState.DEAD);
        assertThat(job.getAttempts()).isEqualTo(3);
        assertThat(job.getNextRunAt()).isNull();
    }

    @Test
    void markSuccessCompletesAndClearsLock() {
        Job job = new Job("job1", "true", 3, 2, 0, null);
        job.setState(JobState.PROCESSING);
        job.setLockedBy("worker-0");
        when(jobRepository.findById("job1")).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        jobService.markSuccess("job1");

        assertThat(job.getState()).isEqualTo(JobState.COMPLETED);
        assertThat(job.getLockedBy()).isNull();
        assertThat(job.getCompletedAt()).isNotNull();
    }

    @Test
    void dlqRetryResetsDeadJobToPending() {
        Job job = new Job("job1", "false", 3, 2, 0, null);
        job.setState(JobState.DEAD);
        job.setAttempts(3);
        job.setLastError("exit code 1");
        when(jobRepository.findById("job1")).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Job retried = jobService.dlqRetry("job1");

        assertThat(retried.getState()).isEqualTo(JobState.PENDING);
        assertThat(retried.getAttempts()).isEqualTo(0);
        assertThat(retried.getLastError()).isNull();
    }

    @Test
    void dlqRetryRejectsJobNotInDeadState() {
        Job job = new Job("job1", "false", 3, 2, 0, null);
        job.setState(JobState.PENDING);
        when(jobRepository.findById("job1")).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.dlqRetry("job1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void dlqRetryRejectsUnknownJob() {
        when(jobRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.dlqRetry("missing"))
                .isInstanceOf(NoSuchElementException.class);
    }
}
