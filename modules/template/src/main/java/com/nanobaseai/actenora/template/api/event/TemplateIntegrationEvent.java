package com.nanobaseai.actenora.template.api.event;

import com.nanobaseai.actenora.sharedkernel.domain.IntegrationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-module integration event for Template.
 * Published via outbox / Spring Modulith — never by leaking domain types.
 */
public record TemplateIntegrationEvent(
        UUID eventId,
        Instant occurredAt,
        String aggregateId
) implements IntegrationEvent {
}
