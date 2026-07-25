package com.nanobaseai.actenora.meeting.api.event;

import com.nanobaseai.actenora.sharedkernel.domain.IntegrationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-module integration event for Meeting.
 * Published via outbox / Spring Modulith — never by leaking domain types.
 */
public record MeetingIntegrationEvent(
        UUID eventId,
        Instant occurredAt,
        String aggregateId
) implements IntegrationEvent {
}
