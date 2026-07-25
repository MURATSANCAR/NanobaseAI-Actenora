package com.nanobaseai.actenora.policy.api.event;

import com.nanobaseai.actenora.sharedkernel.domain.IntegrationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-module integration event for Policy.
 * Published via outbox / Spring Modulith — never by leaking domain types.
 */
public record PolicyIntegrationEvent(
        UUID eventId,
        Instant occurredAt,
        String aggregateId
) implements IntegrationEvent {
}
