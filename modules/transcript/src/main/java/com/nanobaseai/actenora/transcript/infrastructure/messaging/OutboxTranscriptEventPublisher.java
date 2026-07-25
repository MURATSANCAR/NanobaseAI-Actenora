package com.nanobaseai.actenora.transcript.infrastructure.messaging;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxPublisher;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import com.nanobaseai.actenora.transcript.api.event.TranscriptIntegrationEvents;
import com.nanobaseai.actenora.transcript.application.port.out.TranscriptEventPublisher;
import com.nanobaseai.actenora.transcript.domain.Transcript;

import java.util.Objects;
import java.util.UUID;

/**
 * Enqueues transcript integration events into {@code transcript.outbox_event}.
 * Local schema only — no distributed transaction with meeting or other BCs.
 */
public final class OutboxTranscriptEventPublisher implements TranscriptEventPublisher {

    private final OutboxPublisher outboxPublisher;
    private final InstantClock clock;
    private final String producerName;

    public OutboxTranscriptEventPublisher(
            OutboxPublisher outboxPublisher,
            InstantClock clock,
            String producerName) {
        this.outboxPublisher = Objects.requireNonNull(outboxPublisher, "outboxPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.producerName = Objects.requireNonNull(producerName, "producerName");
    }

    @Override
    public void publishIngested(Transcript transcript) {
        UUID eventId = UUID.randomUUID();
        var payload = new TranscriptIntegrationEvents.TranscriptIngested(
                eventId,
                clock.now(),
                transcript.tenantId().value(),
                transcript.id().value(),
                transcript.meetingOccurrenceId(),
                transcript.contentHash().sha256Hex(),
                transcript.status().name());
        outboxPublisher.enqueue(toEnvelope(
                eventId,
                TranscriptIntegrationEvents.TRANSCRIPT_INGESTED,
                transcript.tenantId(),
                transcript.id().value().toString(),
                payloadJson(payload)));
    }

    @Override
    public void publishReady(Transcript transcript, int segmentCount) {
        UUID eventId = UUID.randomUUID();
        var payload = new TranscriptIntegrationEvents.TranscriptReady(
                eventId,
                clock.now(),
                transcript.tenantId().value(),
                transcript.id().value(),
                transcript.meetingOccurrenceId(),
                segmentCount);
        outboxPublisher.enqueue(toEnvelope(
                eventId,
                TranscriptIntegrationEvents.TRANSCRIPT_READY,
                transcript.tenantId(),
                transcript.id().value().toString(),
                payloadJson(payload)));
    }

    private EventEnvelope toEnvelope(
            UUID eventId,
            String eventType,
            TenantId tenantId,
            String aggregateId,
            String payloadJson) {
        return new EventEnvelope(
                eventId,
                eventType,
                1,
                clock.now(),
                tenantId,
                "Transcript",
                aggregateId,
                eventId,
                null,
                null,
                producerName,
                payloadJson);
    }

    private static String payloadJson(TranscriptIntegrationEvents.TranscriptIngested e) {
        return "{"
                + "\"eventId\":\"" + e.eventId() + "\","
                + "\"occurredAt\":\"" + e.occurredAt() + "\","
                + "\"tenantId\":\"" + e.tenantId() + "\","
                + "\"transcriptId\":\"" + e.transcriptId() + "\","
                + "\"meetingOccurrenceId\":\"" + e.meetingOccurrenceId() + "\","
                + "\"contentHash\":\"" + e.contentHash() + "\","
                + "\"status\":\"" + e.status() + "\""
                + "}";
    }

    private static String payloadJson(TranscriptIntegrationEvents.TranscriptReady e) {
        return "{"
                + "\"eventId\":\"" + e.eventId() + "\","
                + "\"occurredAt\":\"" + e.occurredAt() + "\","
                + "\"tenantId\":\"" + e.tenantId() + "\","
                + "\"transcriptId\":\"" + e.transcriptId() + "\","
                + "\"meetingOccurrenceId\":\"" + e.meetingOccurrenceId() + "\","
                + "\"segmentCount\":" + e.segmentCount()
                + "}";
    }
}
