package com.queuectl.repository;

import com.queuectl.domain.Job;
import com.queuectl.domain.JobState;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, String> {

    /**
     * Finds and row-locks the next job eligible for processing: a fresh PENDING job whose
     * scheduled run_at (if any) has elapsed, a FAILED job whose backoff window has elapsed,
     * or a PROCESSING job abandoned by a crashed worker (locked_at older than staleThreshold).
     * SKIP LOCKED lets concurrent workers/processes claim distinct rows without blocking each other.
     */
    @Query(value = "SELECT id FROM jobs " +
            "WHERE (state = 'PENDING' AND (run_at IS NULL OR run_at <= UTC_TIMESTAMP(6))) " +
            "   OR (state = 'FAILED' AND next_run_at <= UTC_TIMESTAMP(6)) " +
            "   OR (state = 'PROCESSING' AND locked_at <= :staleThreshold) " +
            "ORDER BY priority DESC, created_at ASC " +
            "LIMIT 1 FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    Optional<String> findNextClaimableId(@Param("staleThreshold") Instant staleThreshold);

    List<Job> findByStateOrderByCreatedAtDesc(JobState state, Pageable pageable);

    @Query("SELECT j.state AS state, COUNT(j) AS total FROM Job j GROUP BY j.state")
    List<StateCount> countGroupedByState();

    interface StateCount {
        JobState getState();

        long getTotal();
    }
}
