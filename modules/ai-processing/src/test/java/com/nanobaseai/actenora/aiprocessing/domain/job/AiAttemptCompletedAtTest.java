package com.nanobaseai.actenora.aiprocessing.domain.job;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiAttemptCompletedAtTest {

    @Test
    void successCompletedAtIsAfterStartedAtAndTracksLatency() {
        Instant started = Instant.parse("2026-07-28T06:00:00Z");
        Instant completed = started.plusMillis(1_250);
        AiAttempt attempt = AiAttempt.start(
                UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), started);

        attempt.completeSuccess(1_250L, 10, 20, completed);

        assertTrue(attempt.startedAt().isBefore(attempt.completedAt().orElseThrow()));
        assertEquals(1_250L, attempt.latencyMs().orElseThrow());
        assertEquals(
                1_250L,
                Duration.between(attempt.startedAt(), attempt.completedAt().orElseThrow()).toMillis()
        );
        assertEquals(AiAttemptStatus.SUCCEEDED, attempt.status());
    }

    @Test
    void failureCompletedAtIsAfterStartedAtAndKeepsCategory() {
        Instant started = Instant.parse("2026-07-28T06:00:00Z");
        Instant completed = started.plusMillis(800);
        AiAttempt attempt = AiAttempt.start(
                UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), started);

        attempt.completeFailure(800L, true, "INVALID_JSON", "parse failed", completed);

        assertTrue(attempt.startedAt().isBefore(attempt.completedAt().orElseThrow()));
        assertEquals(800L, attempt.latencyMs().orElseThrow());
        assertEquals("INVALID_JSON", attempt.failureCategory().orElseThrow());
        assertEquals(AiAttemptStatus.FAILED, attempt.status());
    }
}
