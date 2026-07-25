package com.nanobaseai.actenora.transcript.extraction;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventBackbone;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxStatus;
import com.nanobaseai.actenora.sharedkernel.messaging.inbox.IdempotentEventConsumer;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.InMemoryOutboxStore;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import com.nanobaseai.actenora.transcript.api.contract.MeetingOccurrenceContracts;
import com.nanobaseai.actenora.transcript.api.event.TranscriptIntegrationEvents;
import com.nanobaseai.actenora.transcript.application.TranscriptIngestionService;
import com.nanobaseai.actenora.transcript.application.VttUploadValidator;
import com.nanobaseai.actenora.transcript.application.port.in.UploadManualVttCommand;
import com.nanobaseai.actenora.transcript.application.port.in.UploadManualVttResult;
import com.nanobaseai.actenora.transcript.infrastructure.messaging.MeetingOccurrenceUpsertedHandler;
import com.nanobaseai.actenora.transcript.infrastructure.messaging.OutboxTranscriptEventPublisher;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryKnownMeetingOccurrenceStore;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryTranscriptRepository;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.InMemoryTranscriptSegmentRepository;
import com.nanobaseai.actenora.transcript.infrastructure.storage.InMemoryObjectStorage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FAZ 26 acceptance simulation: extractable boundaries without Testcontainers.
 */
class TranscriptExtractionSimulationTest {

    private static final Instant FIXED = Instant.parse("2026-07-25T18:00:00Z");
    private static final String VTT = """
            WEBVTT

            00:00:00.000 --> 00:00:02.000
            Hello extraction
            """;

    @Test
    void meetingOccurrenceArrivesViaContract_notCrossSchemaQuery() {
        InMemoryKnownMeetingOccurrenceStore store = new InMemoryKnownMeetingOccurrenceStore();
        MeetingOccurrenceUpsertedHandler handler = new MeetingOccurrenceUpsertedHandler(store);
        EventBackbone backbone = EventBackbone.inMemory("meeting");

        TenantId tenantId = TenantId.random();
        UUID meetingOccurrenceId = UUID.randomUUID();
        EventEnvelope envelope = meetingUpsertedEnvelope(tenantId, meetingOccurrenceId);

        IdempotentEventConsumer consumer = backbone.consumer("transcript");
        assertEquals(
                IdempotentEventConsumer.Outcome.PROCESSED,
                consumer.consume(envelope, handler::handle).outcome());
        assertTrue(store.isKnown(tenantId, meetingOccurrenceId));

        // Duplicate delivery must not re-apply side effects (inbox idempotency).
        AtomicInteger sideEffects = new AtomicInteger();
        assertEquals(
                IdempotentEventConsumer.Outcome.DUPLICATE,
                consumer.consume(envelope, e -> {
                    sideEffects.incrementAndGet();
                    handler.handle(e);
                }).outcome());
        assertEquals(0, sideEffects.get());
    }

    @Test
    void uploadEnqueuesOutbox_survivesPublisherRestartWithoutDataLoss() {
        EventBackbone backbone = EventBackbone.inMemory("transcript");
        InstantClock clock = new InstantClock(Clock.fixed(FIXED, ZoneOffset.UTC));
        TranscriptIngestionService service = new TranscriptIngestionService(
                new InMemoryTranscriptRepository(),
                new InMemoryTranscriptSegmentRepository(),
                new InMemoryObjectStorage(),
                new VttUploadValidator(1024 * 1024),
                clock,
                new OutboxTranscriptEventPublisher(backbone.outboxPublisher(), clock, "transcript"));

        UploadManualVttResult result = service.uploadManualVtt(new UploadManualVttCommand(
                TenantId.random(),
                UUID.randomUUID(),
                "meeting.vtt",
                "text/vtt",
                VTT.getBytes(StandardCharsets.UTF_8),
                "en",
                null));
        assertFalse(result.duplicate());
        assertEquals(1, backbone.outboxStore().countByStatus(OutboxStatus.PENDING));

        // Simulate platform/worker restart: same durable outbox store, new relay process.
        InMemoryOutboxStore durable = (InMemoryOutboxStore) backbone.outboxStore();
        assertFalse(durable.snapshot().isEmpty());

        assertEquals(1, backbone.relay().publishDueBatch());
        assertEquals(0, backbone.outboxStore().countByStatus(OutboxStatus.PENDING));
        assertEquals(1, backbone.outboxStore().countByStatus(OutboxStatus.PUBLISHED));
        assertEquals(
                TranscriptIntegrationEvents.TRANSCRIPT_INGESTED,
                backbone.recordingTransport().published().getFirst().eventType());
    }

    @Test
    void transcriptMigrationsNeverReferenceMeetingSchema() throws Exception {
        Path migrations = Path.of("src/main/resources/db/migration/transcript");
        assertTrue(Files.isDirectory(migrations), "transcript Flyway directory missing");
        try (Stream<Path> files = Files.list(migrations)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                String sql = Files.readString(file).toLowerCase();
                assertFalse(sql.contains("meeting."), () -> "cross-schema reference in " + file);
                assertFalse(sql.contains("references meeting"), () -> "FK to meeting in " + file);
                assertFalse(sql.contains("join meeting"), () -> "join meeting in " + file);
            }
        }
    }

    @Test
    void noDistributedTransaction_outboxIsLocalSchemaOnly() {
        // Documented invariant: TransactionalOutboxPublisher writes transcript.outbox_event only.
        // There is no XA / two-phase commit across meeting + transcript.
        EventBackbone transcriptBackbone = EventBackbone.inMemory("transcript");
        EventBackbone meetingBackbone = EventBackbone.inMemory("meeting");
        assertEquals(0, meetingBackbone.outboxStore().countByStatus(OutboxStatus.PENDING));

        InstantClock clock = new InstantClock(Clock.fixed(FIXED, ZoneOffset.UTC));
        new TranscriptIngestionService(
                new InMemoryTranscriptRepository(),
                new InMemoryTranscriptSegmentRepository(),
                new InMemoryObjectStorage(),
                new VttUploadValidator(1024 * 1024),
                clock,
                new OutboxTranscriptEventPublisher(transcriptBackbone.outboxPublisher(), clock, "transcript"))
                .uploadManualVtt(new UploadManualVttCommand(
                        TenantId.random(),
                        UUID.randomUUID(),
                        "meeting.vtt",
                        "text/vtt",
                        VTT.getBytes(StandardCharsets.UTF_8),
                        null,
                        null));

        assertEquals(1, transcriptBackbone.outboxStore().countByStatus(OutboxStatus.PENDING));
        assertEquals(0, meetingBackbone.outboxStore().countByStatus(OutboxStatus.PENDING));
    }

    private static EventEnvelope meetingUpsertedEnvelope(TenantId tenantId, UUID meetingOccurrenceId) {
        UUID eventId = UUID.randomUUID();
        String payload = "{"
                + "\"tenantId\":\"" + tenantId.value() + "\","
                + "\"meetingOccurrenceId\":\"" + meetingOccurrenceId + "\""
                + "}";
        return new EventEnvelope(
                eventId,
                MeetingOccurrenceContracts.MEETING_OCCURRENCE_UPSERTED,
                1,
                FIXED,
                tenantId,
                "MeetingOccurrence",
                meetingOccurrenceId.toString(),
                eventId,
                null,
                null,
                "meeting",
                payload);
    }
}
