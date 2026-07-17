package com.queuectl.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnqueueRequestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesMinimalPayload() throws Exception {
        EnqueueRequest req = mapper.readValue("{\"id\":\"job1\",\"command\":\"sleep 2\"}", EnqueueRequest.class);

        assertThat(req.getId()).isEqualTo("job1");
        assertThat(req.getCommand()).isEqualTo("sleep 2");
        assertThat(req.getMaxRetries()).isNull();
    }

    @Test
    void ignoresServerManagedFieldsFromTheFullJobSpecExample() throws Exception {
        String fullJobJson = "{"
                + "\"id\":\"unique-job-id\","
                + "\"command\":\"echo 'Hello World'\","
                + "\"state\":\"pending\","
                + "\"attempts\":0,"
                + "\"max_retries\":3,"
                + "\"created_at\":\"2025-11-04T10:30:00Z\","
                + "\"updated_at\":\"2025-11-04T10:30:00Z\""
                + "}";

        EnqueueRequest req = mapper.readValue(fullJobJson, EnqueueRequest.class);

        assertThat(req.getId()).isEqualTo("unique-job-id");
        assertThat(req.getCommand()).isEqualTo("echo 'Hello World'");
        assertThat(req.getMaxRetries()).isEqualTo(3);
    }
}
