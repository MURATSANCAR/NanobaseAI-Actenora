package com.nanobaseai.actenora.aiprocessing.domain.job;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiJobRetryBackoffTest {

    @Test
    void retryBackoffCapsAtFifteenMinutes() {
        assertEquals(Duration.ofSeconds(30), AiJob.retryBackoff(1));
        assertEquals(Duration.ofSeconds(60), AiJob.retryBackoff(2));
        assertEquals(Duration.ofMinutes(15), AiJob.retryBackoff(10));
    }

    @Test
    void markFailedRetryableSetsNextEligibleAt() {
        Instant now = Instant.parse("2026-07-27T06:00:00Z");
        AiJob job = runningJob(now);
        job.markFailed(true, now);
        assertEquals(AiJobStatus.QUEUED, job.status());
        assertTrue(job.nextEligibleAt().isPresent());
        assertEquals(now.plus(Duration.ofSeconds(30)), job.nextEligibleAt().orElseThrow());
        assertFalse(job.isEligibleAt(now));
        assertTrue(job.isEligibleAt(now.plus(Duration.ofSeconds(30))));
    }

    @Test
    void markDeadFromStaleIsTerminal() {
        Instant now = Instant.parse("2026-07-27T06:00:00Z");
        AiJob job = runningJob(now);
        job.markDeadFromStale(now);
        assertEquals(AiJobStatus.DEAD, job.status());
        assertTrue(job.completedAt().isPresent());
    }

    private static AiJob runningJob(Instant now) {
        UUID model = UUID.randomUUID();
        UUID deployment = UUID.randomUUID();
        AiJob job = AiJob.enqueue(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "CHUNK_EXTRACTION",
                JobPriority.NORMAL,
                AiCapability.TRANSCRIPT_EXTRACTION,
                "pv-meeting-chunk-extraction-v1",
                "extraction-output.v1",
                "tr",
                22,
                true,
                now,
                now.plus(Duration.ofHours(1)),
                UUID.randomUUID()
        );
        job.applyRoute(new SelectedRoute(model, deployment, "m", "test", java.util.List.of(), now));
        job.markRunning(now);
        return job;
    }
}
