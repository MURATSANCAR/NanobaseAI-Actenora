package com.nanobaseai.actenora.observability;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiiRedactorTest {

    @Test
    void redactsBlockedKeys() {
        assertEquals(PiiRedactor.REDACTED, PiiRedactor.redactValue("transcript", "hello world transcript body"));
        assertEquals(PiiRedactor.REDACTED, PiiRedactor.redactValue("api_key", "sk-123"));
        assertEquals(PiiRedactor.REDACTED, PiiRedactor.redactValue("password", "hunter2"));
        assertEquals(PiiRedactor.REDACTED, PiiRedactor.redactValue("accessToken", "tok"));
    }

    @Test
    void redactsSecretsEmbeddedInSafeKeys() {
        String redacted = PiiRedactor.redactValue(
                "detail",
                "Authorization: Bearer abc.def.ghi user=ada@example.com"
        );
        assertFalse(redacted.contains("abc.def.ghi"));
        assertFalse(redacted.contains("ada@example.com"));
        assertTrue(redacted.contains(PiiRedactor.REDACTED));
    }

    @Test
    void transcriptAbsenceInLogLine() {
        String snippet = "Quarterly OKR alignment with finance";
        String log = StructuredLogEvent.info("transcript-worker", "fetched")
                .withField("transcript", snippet)
                .withField("meetingId", "m-42")
                .toJson();
        assertFalse(PiiRedactor.containsTranscriptLeak(log, snippet));
        assertTrue(log.contains("m-42"));
    }

    @Test
    void redactMapLeavesSafeFields() {
        Map<String, String> safe = PiiRedactor.redactMap(Map.of(
                "jobId", "j-1",
                "prompt", "do not log me",
                "modelId", "gpt-x"
        ));
        assertEquals("j-1", safe.get("jobId"));
        assertEquals("gpt-x", safe.get("modelId"));
        assertEquals(PiiRedactor.REDACTED, safe.get("prompt"));
    }
}
