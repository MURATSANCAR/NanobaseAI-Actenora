package com.nanobaseai.actenora.sharedkernel.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable inbox row aligned with {@code <schema>.inbox_event}.
 * Primary key is ({@code consumerName}, {@code eventId}) for consumer idempotency.
 */
public final class InboxEvent {

    private final String consumerName;
    private final UUID eventId;
    private final Instant receivedAt;
    private Instant processedAt;
    private InboxStatus status;
    private String failureCode;

    public InboxEvent(
            String consumerName,
            UUID eventId,
            Instant receivedAt,
            Instant processedAt,
            InboxStatus status,
            String failureCode
    ) {
        this.consumerName = Objects.requireNonNull(consumerName, "consumerName");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        this.processedAt = processedAt;
        this.status = Objects.requireNonNull(status, "status");
        this.failureCode = failureCode;
    }

    public static InboxEvent received(String consumerName, UUID eventId, Instant now) {
        return new InboxEvent(consumerName, eventId, now, null, InboxStatus.RECEIVED, null);
    }

    public String consumerName() {
        return consumerName;
    }

    public UUID eventId() {
        return eventId;
    }

    public Instant receivedAt() {
        return receivedAt;
    }

    public Optional<Instant> processedAt() {
        return Optional.ofNullable(processedAt);
    }

    public InboxStatus status() {
        return status;
    }

    public Optional<String> failureCode() {
        return Optional.ofNullable(failureCode);
    }

    public boolean isTerminalSuccess() {
        return status == InboxStatus.PROCESSED;
    }

    public void markProcessing() {
        this.status = InboxStatus.PROCESSING;
    }

    public void markProcessed(Instant at) {
        this.status = InboxStatus.PROCESSED;
        this.processedAt = Objects.requireNonNull(at, "at");
        this.failureCode = null;
    }

    public void markFailed(String failureCode) {
        this.status = InboxStatus.FAILED;
        this.failureCode = failureCode;
    }

    public void markDeadLetter(String failureCode) {
        this.status = InboxStatus.DEAD_LETTER;
        this.failureCode = failureCode;
    }

    public void resetForReplay() {
        this.status = InboxStatus.RECEIVED;
        this.processedAt = null;
        this.failureCode = null;
    }
}
