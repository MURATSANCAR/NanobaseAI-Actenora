package com.nanobaseai.actenora.template.domain.event;

import com.nanobaseai.actenora.sharedkernel.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Module-internal domain event. Not published across module boundaries.
 */
public record TemplateDomainEvent(
        UUID eventId,
        Instant occurredAt,
        String aggregateId
) implements DomainEvent {
}
