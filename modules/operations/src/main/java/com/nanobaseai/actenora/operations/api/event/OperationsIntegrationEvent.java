package com.nanobaseai.actenora.operations.api.event;

import com.nanobaseai.actenora.operations.domain.AlertSeverity;
import com.nanobaseai.actenora.operations.domain.AlertType;
import com.nanobaseai.actenora.sharedkernel.domain.IntegrationEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Cross-module integration event for Operations alerts (FAZ 25).
 */
public record OperationsIntegrationEvent(
        UUID eventId,
        Instant occurredAt,
        String aggregateId,
        AlertType alertType,
        AlertSeverity severity,
        String title
) implements IntegrationEvent {

    public OperationsIntegrationEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(alertType, "alertType");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(title, "title");
    }

    public static OperationsIntegrationEvent alertRaised(
            UUID alertId,
            Instant occurredAt,
            AlertType type,
            AlertSeverity severity,
            String title
    ) {
        return new OperationsIntegrationEvent(UUID.randomUUID(), occurredAt, alertId.toString(), type, severity, title);
    }
}
