package com.queuectl.web;

import com.queuectl.domain.JobState;
import com.queuectl.service.JobService;
import com.queuectl.service.WorkerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Read-only(-ish) JSON API behind the static dashboard at "/". Reuses the same JobService /
 * WorkerService the CLI commands call, so the dashboard can never drift from CLI behavior.
 */
@RestController
@RequestMapping("/api")
public class DashboardController {

    private final JobService jobService;
    private final WorkerService workerService;

    public DashboardController(JobService jobService, WorkerService workerService) {
        this.jobService = jobService;
        this.workerService = workerService;
    }

    @GetMapping("/status")
    public StatusResponse status() {
        Map<String, Long> states = new LinkedHashMap<>();
        for (JobState state : JobState.values()) {
            states.put(state.name().toLowerCase(), 0L);
        }
        jobService.countByState().forEach((state, count) -> states.put(state.name().toLowerCase(), count));

        List<WorkerResponse> workers = workerService.activeWorkers().stream()
                .map(WorkerResponse::from)
                .toList();

        return new StatusResponse(states, workers);
    }

    @GetMapping("/jobs")
    public List<JobResponse> jobs(@RequestParam(required = false) String state,
                                   @RequestParam(defaultValue = "50") int limit) {
        JobState jobState = parseState(state);
        return jobService.list(jobState, limit).stream().map(JobResponse::from).toList();
    }

    @GetMapping("/dlq")
    public List<JobResponse> dlq(@RequestParam(defaultValue = "50") int limit) {
        return jobService.dlqList(limit).stream().map(JobResponse::from).toList();
    }

    @PostMapping("/dlq/{id}/retry")
    public JobResponse retry(@PathVariable String id) {
        try {
            return JobResponse.from(jobService.dlqRetry(id));
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    private static JobState parseState(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        try {
            return JobState.valueOf(state.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown state '" + state + "'");
        }
    }
}
