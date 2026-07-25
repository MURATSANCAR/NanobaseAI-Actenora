package com.nanobaseai.actenora.modelmanagement.api.event;

import com.nanobaseai.actenora.sharedkernel.domain.IntegrationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-module integration event for Model Management.
 * Published via outbox / Spring Modulith — never by leaking domain types.
 */
public record ModelManagementIntegrationEvent(
        UUID eventId,
        Instant occurredAt,
        String aggregateId
) implements IntegrationEvent {
}
