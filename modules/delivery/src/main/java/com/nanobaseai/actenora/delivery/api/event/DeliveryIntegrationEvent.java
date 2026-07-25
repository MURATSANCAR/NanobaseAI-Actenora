package com.nanobaseai.actenora.delivery.api.event;

import com.nanobaseai.actenora.sharedkernel.domain.IntegrationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-module integration event for Delivery.
 * Published via outbox / Spring Modulith — never by leaking domain types.
 */
public record DeliveryIntegrationEvent(
        UUID eventId,
        Instant occurredAt,
        String aggregateId
) implements IntegrationEvent {
}
