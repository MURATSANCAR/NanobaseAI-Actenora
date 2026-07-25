package com.nanobaseai.actenora.sharedkernel.messaging;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Poison / exhausted message retained for operator replay (DB-backed DLQ complementary to broker DLX).
 */
public record DeadLetterEvent(
        UUID id,
        DeadLetterSource source,
        UUID eventId,
        String consumerName,
        String eventType,
        int eventVersion,
        String payloadJson,
        String failureCode,
        String failureDetail,
        UUID correlationId,
        TenantId tenantId,
        int attempts,
        Instant deadLetteredAt,
        Instant replayedAt
) {

    public DeadLetterEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(payloadJson, "payloadJson");
        Objects.requireNonNull(failureCode, "failureCode");
        Objects.requireNonNull(deadLetteredAt, "deadLetteredAt");
    }

    public Optional<String> consumerNameOptional() {
        return Optional.ofNullable(consumerName);
    }

    public Optional<String> failureDetailOptional() {
        return Optional.ofNullable(failureDetail);
    }

    public Optional<UUID> correlationIdOptional() {
        return Optional.ofNullable(correlationId);
    }

    public Optional<TenantId> tenantIdOptional() {
        return Optional.ofNullable(tenantId);
    }

    public Optional<Instant> replayedAtOptional() {
        return Optional.ofNullable(replayedAt);
    }

    public DeadLetterEvent markReplayed(Instant at) {
        return new DeadLetterEvent(
                id,
                source,
                eventId,
                consumerName,
                eventType,
                eventVersion,
                payloadJson,
                failureCode,
                failureDetail,
                correlationId,
                tenantId,
                attempts,
                deadLetteredAt,
                Objects.requireNonNull(at, "at")
        );
    }

    public enum DeadLetterSource {
        OUTBOX,
        INBOX,
        TRANSPORT
    }
}
