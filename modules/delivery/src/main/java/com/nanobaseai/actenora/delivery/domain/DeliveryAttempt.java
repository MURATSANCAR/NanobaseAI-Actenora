package com.nanobaseai.actenora.delivery.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One send try against a mail provider. Distinct from provider acceptance vs final delivery.
 */
public final class DeliveryAttempt {

    private final UUID id;
    private final int attemptNumber;
    private DeliveryStatus status;
    private final Instant startedAt;
    private Instant finishedAt;
    private String failureCode;
    private String failureDetail;
    private ProviderMessage providerMessage;

    private DeliveryAttempt(
            UUID id,
            int attemptNumber,
            DeliveryStatus status,
            Instant startedAt,
            Instant finishedAt,
            String failureCode,
            String failureDetail,
            ProviderMessage providerMessage
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.attemptNumber = attemptNumber;
        this.status = Objects.requireNonNull(status, "status");
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.finishedAt = finishedAt;
        this.failureCode = failureCode;
        this.failureDetail = failureDetail;
        this.providerMessage = providerMessage;
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be >= 1");
        }
    }

    public static DeliveryAttempt rehydrate(
            UUID id,
            int attemptNumber,
            DeliveryStatus status,
            Instant startedAt,
            Instant finishedAt,
            String failureCode,
            String failureDetail,
            ProviderMessage providerMessage
    ) {
        return new DeliveryAttempt(
                id, attemptNumber, status, startedAt, finishedAt, failureCode, failureDetail, providerMessage
        );
    }

    public static DeliveryAttempt start(int attemptNumber, Instant now) {
        return new DeliveryAttempt(
                UUID.randomUUID(),
                attemptNumber,
                DeliveryStatus.SENDING,
                now,
                null,
                null,
                null,
                null
        );
    }

    public void markProviderAccepted(ProviderMessage message, Instant now) {
        Objects.requireNonNull(message, "message");
        this.providerMessage = message;
        this.status = DeliveryStatus.PROVIDER_ACCEPTED;
        this.finishedAt = now;
        this.failureCode = null;
        this.failureDetail = null;
    }

    public void markDelivered(Instant now) {
        if (providerMessage == null) {
            throw new DeliveryDomainException(
                    "MISSING_PROVIDER_MESSAGE",
                    "cannot deliver without provider acceptance");
        }
        this.providerMessage = providerMessage.markDelivered(now);
        this.status = DeliveryStatus.DELIVERED;
        this.finishedAt = now;
    }

    public void markDeferred(String code, String detail, Instant now) {
        this.status = DeliveryStatus.DEFERRED;
        this.finishedAt = now;
        this.failureCode = code;
        this.failureDetail = detail;
    }

    public void markBounced(String code, String detail, Instant now) {
        this.status = DeliveryStatus.BOUNCED;
        this.finishedAt = now;
        this.failureCode = code;
        this.failureDetail = detail;
    }

    public void markFailed(String code, String detail, Instant now) {
        this.status = DeliveryStatus.FAILED;
        this.finishedAt = now;
        this.failureCode = code;
        this.failureDetail = detail;
    }

    public UUID id() {
        return id;
    }

    public int attemptNumber() {
        return attemptNumber;
    }

    public DeliveryStatus status() {
        return status;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Optional<Instant> finishedAt() {
        return Optional.ofNullable(finishedAt);
    }

    public Optional<String> failureCode() {
        return Optional.ofNullable(failureCode);
    }

    public Optional<String> failureDetail() {
        return Optional.ofNullable(failureDetail);
    }

    public Optional<ProviderMessage> providerMessage() {
        return Optional.ofNullable(providerMessage);
    }

    public boolean providerAcceptedButNotDelivered() {
        return status == DeliveryStatus.PROVIDER_ACCEPTED
                && providerMessage != null
                && !providerMessage.isDelivered();
    }
}
