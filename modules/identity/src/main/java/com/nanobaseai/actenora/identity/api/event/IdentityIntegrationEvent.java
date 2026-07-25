package com.nanobaseai.actenora.identity.api.event;

import com.nanobaseai.actenora.sharedkernel.domain.IntegrationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-module integration event for Identity.
 * Published via outbox / Spring Modulith — never by leaking domain types.
 */
public record IdentityIntegrationEvent(
        UUID eventId,
        Instant occurredAt,
        String aggregateId
) implements IntegrationEvent {
}
