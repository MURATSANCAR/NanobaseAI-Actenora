package com.nanobaseai.actenora.security.messaging;

import com.nanobaseai.actenora.aiprocessing.api.AiProcessingApi;
import com.nanobaseai.actenora.aiprocessing.application.port.AdmissionController;
import com.nanobaseai.actenora.aiprocessing.application.port.JobScheduler;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiCapability;
import com.nanobaseai.actenora.aiprocessing.domain.job.AiJob;
import com.nanobaseai.actenora.aiprocessing.domain.job.JobPriority;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.transcript.api.event.TranscriptIntegrationEvents;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TranscriptReadyAiAdmissionHandlerTest {

    @Test
    void admitsChunkExtractionJobFromTranscriptReadyPayload() {
        AtomicReference<AdmissionController.SubmitAiJobCommand> captured = new AtomicReference<>();
        AiProcessingApi api = new StubAiApi(command -> {
            captured.set(command);
            AiJob job = org.mockito.Mockito.mock(AiJob.class);
            org.mockito.Mockito.when(job.id()).thenReturn(UUID.randomUUID());
            return AdmissionController.AdmissionDecision.accepted(job, Duration.ZERO);
        });
        TranscriptReadyAiAdmissionHandler handler = new TranscriptReadyAiAdmissionHandler(api);

        UUID tenantId = UUID.randomUUID();
        UUID transcriptId = UUID.randomUUID();
        UUID meetingId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-26T10:00:00Z");
        String payload = "{"
                + "\"eventId\":\"" + eventId + "\","
                + "\"occurredAt\":\"" + occurredAt + "\","
                + "\"tenantId\":\"" + tenantId + "\","
                + "\"transcriptId\":\"" + transcriptId + "\","
                + "\"meetingOccurrenceId\":\"" + meetingId + "\","
                + "\"segmentCount\":3"
                + "}";

        handler.handle(new EventEnvelope(
                eventId,
                TranscriptIntegrationEvents.TRANSCRIPT_READY,
                1,
                occurredAt,
                TenantId.of(tenantId),
                "Transcript",
                transcriptId.toString(),
                eventId,
                null,
                null,
                "transcript",
                payload
        ));

        AdmissionController.SubmitAiJobCommand command = captured.get();
        assertNotNull(command);
        assertEquals(tenantId, command.tenantId());
        assertEquals(meetingId, command.meetingOccurrenceId());
        assertEquals(transcriptId, command.transcriptId());
        assertEquals("CHUNK_EXTRACTION", command.taskType());
        assertEquals(AiCapability.TRANSCRIPT_EXTRACTION, command.requestedCapability());
        assertEquals(JobPriority.NORMAL, command.priority());
        assertEquals(eventId, command.correlationId());
    }

    @Test
    void ignoresNonTranscriptReadyEvents() {
        AtomicBoolean called = new AtomicBoolean(false);
        AiProcessingApi api = new StubAiApi(command -> {
            called.set(true);
            return AdmissionController.AdmissionDecision.rejected("should-not-run");
        });
        TranscriptReadyAiAdmissionHandler handler = new TranscriptReadyAiAdmissionHandler(api);
        handler.handle(new EventEnvelope(
                UUID.randomUUID(),
                "other.Event.v1",
                1,
                Instant.now(),
                TenantId.random(),
                "X",
                "1",
                UUID.randomUUID(),
                null,
                null,
                "x",
                "{}"
        ));
        assertFalse(called.get());
    }

    @FunctionalInterface
    private interface SubmitFn {
        AdmissionController.AdmissionDecision apply(AdmissionController.SubmitAiJobCommand command);
    }

    private static final class StubAiApi implements AiProcessingApi {
        private final SubmitFn submitFn;

        private StubAiApi(SubmitFn submitFn) {
            this.submitFn = submitFn;
        }

        @Override
        public AdmissionController.AdmissionDecision submitJob(AdmissionController.SubmitAiJobCommand command) {
            return submitFn.apply(command);
        }

        @Override
        public AiJob cancelJob(UUID jobId, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AiJob adminOverrideRoute(
                UUID jobId,
                UUID modelDefinitionId,
                UUID deploymentId,
                String modelKey,
                boolean actorIsAdmin,
                Instant now
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<JobScheduler.ClaimedJob> claimNext(Instant now) {
            return Optional.empty();
        }

        @Override
        public int recoverStaleRunning(Instant now, Duration staleAfter) {
            return 0;
        }

        @Override
        public Optional<AiJob> findJob(UUID jobId) {
            return Optional.empty();
        }
    }
}
