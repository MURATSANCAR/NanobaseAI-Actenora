package com.nanobaseai.actenora.observability;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredLogEventTest {

    @Test
    void toJsonIncludesServiceLevelAndMessage() {
        String json = StructuredLogEvent.info("platform-backend", "boot")
                .withField("port", "8080")
                .toJson();
        assertTrue(json.contains("\"service\":\"platform-backend\""));
        assertTrue(json.contains("\"level\":\"INFO\""));
        assertTrue(json.contains("\"message\":\"boot\""));
        assertTrue(json.contains("\"port\":\"8080\""));
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
    }

    @Test
    void withCorrelationAddsStandardIds() {
        String json = StructuredLogEvent.info("ai-worker", "routed")
                .withCorrelation("corr-1", "evt-1", "job-1", "model-1", "dep-1")
                .toJson();
        assertTrue(json.contains("\"correlationId\":\"corr-1\""));
        assertTrue(json.contains("\"eventId\":\"evt-1\""));
        assertTrue(json.contains("\"jobId\":\"job-1\""));
        assertTrue(json.contains("\"modelId\":\"model-1\""));
        assertTrue(json.contains("\"deploymentId\":\"dep-1\""));
    }

    @Test
    void toJsonRedactsSecretsAndTranscriptFields() {
        String json = StructuredLogEvent.of(
                "INFO",
                "platform-backend",
                "ingest",
                Map.of(
                        "apiKey", "sk-live-secret",
                        "transcript", "CONFIDENTIAL meeting notes about Acme",
                        "meetingId", "m-1"
                )
        ).toJson();
        assertFalse(json.contains("sk-live-secret"));
        assertFalse(json.contains("CONFIDENTIAL meeting notes"));
        assertTrue(json.contains(PiiRedactor.REDACTED));
        assertTrue(json.contains("\"meetingId\":\"m-1\""));
    }

    @Test
    void renderDelegatesToJson() {
        StructuredLogEvent event = StructuredLogEvent.info("svc", "msg");
        assertEquals(event.toJson(), event.render());
    }
}
