package com.nanobaseai.actenora.meetingintelligence.api.event;

import com.nanobaseai.actenora.sharedkernel.domain.IntegrationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-module integration event for Meeting Intelligence.
 * Published via outbox / Spring Modulith — never by leaking domain types.
 */
public record MeetingIntelligenceIntegrationEvent(
        UUID eventId,
        Instant occurredAt,
        String aggregateId
) implements IntegrationEvent {
}
