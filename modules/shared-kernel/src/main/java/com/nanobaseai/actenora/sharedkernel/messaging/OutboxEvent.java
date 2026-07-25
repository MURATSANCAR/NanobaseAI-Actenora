package com.nanobaseai.actenora.sharedkernel.messaging;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable outbox row aligned with {@code <schema>.outbox_event}.
 */
public final class OutboxEvent {

    private final UUID id;
    private final String aggregateType;
    private final String aggregateId;
    private final TenantId tenantId;
    private final String eventType;
    private final int eventVersion;
    private final String payloadJson;
    private final UUID correlationId;
    private final UUID causationId;
    private final String traceId;
    private final Instant occurredAt;
    private Instant publishedAt;
    private OutboxStatus status;
    private int attemptCount;
    private Instant nextAttemptAt;
    private String failureCode;

    public OutboxEvent(
            UUID id,
            String aggregateType,
            String aggregateId,
            TenantId tenantId,
            String eventType,
            int eventVersion,
            String payloadJson,
            UUID correlationId,
            UUID causationId,
            String traceId,
            Instant occurredAt,
            Instant publishedAt,
            OutboxStatus status,
            int attemptCount,
            Instant nextAttemptAt,
            String failureCode
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.eventVersion = eventVersion;
        this.payloadJson = Objects.requireNonNull(payloadJson, "payloadJson");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId");
        this.causationId = causationId;
        this.traceId = traceId;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.publishedAt = publishedAt;
        this.status = Objects.requireNonNull(status, "status");
        this.attemptCount = attemptCount;
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        this.failureCode = failureCode;
    }

    public static OutboxEvent pending(EventEnvelope envelope, Instant now) {
        return new OutboxEvent(
                envelope.eventId(),
                envelope.aggregateType(),
                envelope.aggregateId(),
                envelope.tenantId(),
                envelope.eventType(),
                envelope.eventVersion(),
                envelope.payloadJson(),
                envelope.correlationId(),
                envelope.causationId(),
                envelope.traceId(),
                envelope.occurredAt(),
                null,
                OutboxStatus.PENDING,
                0,
                now,
                null
        );
    }

    public EventEnvelope toEnvelope(String producer) {
        return new EventEnvelope(
                id,
                eventType,
                eventVersion,
                occurredAt,
                tenantId,
                aggregateType,
                aggregateId,
                correlationId,
                causationId,
                traceId,
                producer,
                payloadJson
        );
    }

    public UUID id() {
        return id;
    }

    public String aggregateType() {
        return aggregateType;
    }

    public String aggregateId() {
        return aggregateId;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public String eventType() {
        return eventType;
    }

    public int eventVersion() {
        return eventVersion;
    }

    public String payloadJson() {
        return payloadJson;
    }

    public UUID correlationId() {
        return correlationId;
    }

    public Optional<UUID> causationId() {
        return Optional.ofNullable(causationId);
    }

    public Optional<String> traceId() {
        return Optional.ofNullable(traceId);
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public Optional<Instant> publishedAt() {
        return Optional.ofNullable(publishedAt);
    }

    public OutboxStatus status() {
        return status;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    public Optional<String> failureCode() {
        return Optional.ofNullable(failureCode);
    }

    public void markPublishing() {
        this.status = OutboxStatus.PUBLISHING;
    }

    public void markPublished(Instant at) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Objects.requireNonNull(at, "at");
        this.failureCode = null;
    }

    public void scheduleRetry(Instant nextAttemptAt, String failureCode) {
        this.status = OutboxStatus.RETRY;
        this.attemptCount += 1;
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        this.failureCode = failureCode;
    }

    public void markDeadLetter(String failureCode) {
        this.status = OutboxStatus.DEAD_LETTER;
        this.attemptCount += 1;
        this.failureCode = failureCode;
    }

    /**
     * Safe replay: move DEAD_LETTER / PUBLISHED back to PENDING without changing identity.
     */
    public void resetForReplay(Instant now) {
        this.status = OutboxStatus.PENDING;
        this.publishedAt = null;
        this.nextAttemptAt = Objects.requireNonNull(now, "now");
        this.failureCode = null;
    }
}
