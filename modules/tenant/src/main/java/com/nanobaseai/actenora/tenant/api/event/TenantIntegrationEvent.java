package com.nanobaseai.actenora.tenant.api.event;

import com.nanobaseai.actenora.sharedkernel.domain.IntegrationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-module integration event for Tenant.
 * Published via outbox / Spring Modulith — never by leaking domain types.
 */
public record TenantIntegrationEvent(
        UUID eventId,
        Instant occurredAt,
        String aggregateId
) implements IntegrationEvent {
}
