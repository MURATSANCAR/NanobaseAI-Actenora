package com.nanobaseai.actenora.meetingintelligence.api.event;

import com.nanobaseai.actenora.sharedkernel.domain.IntegrationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-module integration events for Meeting Intelligence (outbox → broker).
 */
public final class MeetingIntelligenceIntegrationEvents {

    /** Approved note version should be projected into the continuity ledger. */
    public static final String NOTE_APPROVED_FOR_LEDGER =
            "meetingintelligence.NoteApprovedForLedger.v1";

    private MeetingIntelligenceIntegrationEvents() {
    }

    public record NoteApprovedForLedger(
            UUID eventId,
            Instant occurredAt,
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID noteVersionId
    ) implements IntegrationEvent {
    }
}
