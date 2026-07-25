package com.nanobaseai.actenora.microsoftconnection.api.event;

import com.nanobaseai.actenora.sharedkernel.domain.IntegrationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-module integration event for Microsoft Connection.
 * Published via outbox / Spring Modulith — never by leaking domain types.
 */
public record MicrosoftConnectionIntegrationEvent(
        UUID eventId,
        Instant occurredAt,
        String aggregateId
) implements IntegrationEvent {
}
