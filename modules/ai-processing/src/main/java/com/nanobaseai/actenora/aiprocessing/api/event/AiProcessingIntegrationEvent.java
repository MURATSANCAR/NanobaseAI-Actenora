package com.nanobaseai.actenora.aiprocessing.api.event;

import com.nanobaseai.actenora.sharedkernel.domain.IntegrationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-module integration event for AI Processing.
 * Published via outbox / Spring Modulith — never by leaking domain types.
 */
public record AiProcessingIntegrationEvent(
        UUID eventId,
        Instant occurredAt,
        String aggregateId
) implements IntegrationEvent {
}
