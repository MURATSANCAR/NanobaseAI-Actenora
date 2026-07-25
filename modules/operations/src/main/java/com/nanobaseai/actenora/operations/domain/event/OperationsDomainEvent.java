package com.nanobaseai.actenora.operations.domain.event;

import com.nanobaseai.actenora.operations.domain.AlertSeverity;
import com.nanobaseai.actenora.operations.domain.AlertType;
import com.nanobaseai.actenora.sharedkernel.domain.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Module-internal domain event for raised ops alerts.
 */
public record OperationsDomainEvent(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        AlertType alertType,
        AlertSeverity severity
) implements DomainEvent {

    public OperationsDomainEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(alertType, "alertType");
        Objects.requireNonNull(severity, "severity");
    }
}
