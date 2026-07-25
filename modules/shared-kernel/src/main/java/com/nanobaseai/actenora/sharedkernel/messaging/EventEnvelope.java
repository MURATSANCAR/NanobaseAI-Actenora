package com.nanobaseai.actenora.sharedkernel.messaging;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Wire-level event envelope ({@code actenora.event.envelope.v1}).
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        TenantId tenantId,
        String aggregateType,
        String aggregateId,
        UUID correlationId,
        UUID causationId,
        String traceId,
        String producer,
        String payloadJson
) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        if (eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be >= 1");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(payloadJson, "payloadJson");
    }

    public Optional<UUID> optionalCausationId() {
        return Optional.ofNullable(causationId);
    }

    public Optional<String> optionalTraceId() {
        return Optional.ofNullable(traceId);
    }

    public Optional<String> optionalProducer() {
        return Optional.ofNullable(producer);
    }
}
