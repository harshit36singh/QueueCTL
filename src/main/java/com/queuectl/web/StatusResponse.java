package com.queuectl.web;

import java.util.List;
import java.util.Map;

public record StatusResponse(Map<String, Long> states, List<WorkerResponse> workers) {
}
