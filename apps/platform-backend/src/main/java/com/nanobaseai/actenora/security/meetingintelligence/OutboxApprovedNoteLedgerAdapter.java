package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.meetingintelligence.api.event.MeetingIntelligenceIntegrationEvents;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedNoteLedgerPort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxPublisher;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * FAZ 29 — enqueues note-approved ledger handoff via transactional outbox
 * (does not write the continuity ledger synchronously).
 */
public final class OutboxApprovedNoteLedgerAdapter implements ApprovedNoteLedgerPort {

    private final OutboxPublisher outboxPublisher;
    private final String producerName;

    public OutboxApprovedNoteLedgerAdapter(OutboxPublisher outboxPublisher, String producerName) {
        this.outboxPublisher = Objects.requireNonNull(outboxPublisher, "outboxPublisher");
        this.producerName = Objects.requireNonNull(producerName, "producerName");
    }

    @Override
    public void append(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID noteVersionId
    ) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        String payload = "{"
                + "\"eventId\":\"" + eventId + "\","
                + "\"occurredAt\":\"" + now + "\","
                + "\"tenantId\":\"" + tenantId.value() + "\","
                + "\"meetingOccurrenceId\":\"" + meetingOccurrenceId + "\","
                + "\"noteId\":\"" + noteId + "\","
                + "\"noteVersionId\":\"" + noteVersionId + "\""
                + "}";
        outboxPublisher.enqueue(new EventEnvelope(
                eventId,
                MeetingIntelligenceIntegrationEvents.NOTE_APPROVED_FOR_LEDGER,
                1,
                now,
                tenantId,
                "MeetingNote",
                noteId.toString(),
                eventId,
                null,
                null,
                producerName,
                payload
        ));
    }
}
