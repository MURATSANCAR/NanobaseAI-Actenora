package com.nanobaseai.actenora.delivery.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Exhausted / poison delivery retained for operator replay.
 */
public record DeliveryDeadLetter(
        UUID id,
        UUID deliveryRequestId,
        TenantId tenantId,
        UUID noteVersionId,
        String recipientEmail,
        String failureCode,
        String failureDetail,
        int attempts,
        Instant deadLetteredAt,
        Instant replayedAt
) {

    public DeliveryDeadLetter {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(deliveryRequestId, "deliveryRequestId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(noteVersionId, "noteVersionId");
        Objects.requireNonNull(recipientEmail, "recipientEmail");
        Objects.requireNonNull(failureCode, "failureCode");
        Objects.requireNonNull(deadLetteredAt, "deadLetteredAt");
    }

    public static DeliveryDeadLetter open(
            UUID deliveryRequestId,
            TenantId tenantId,
            UUID noteVersionId,
            String recipientEmail,
            String failureCode,
            String failureDetail,
            int attempts,
            Instant at
    ) {
        return new DeliveryDeadLetter(
                UUID.randomUUID(),
                deliveryRequestId,
                tenantId,
                noteVersionId,
                recipientEmail,
                failureCode,
                failureDetail,
                attempts,
                at,
                null
        );
    }

    public Optional<Instant> replayedAtOptional() {
        return Optional.ofNullable(replayedAt);
    }

    public DeliveryDeadLetter markReplayed(Instant at) {
        return new DeliveryDeadLetter(
                id,
                deliveryRequestId,
                tenantId,
                noteVersionId,
                recipientEmail,
                failureCode,
                failureDetail,
                attempts,
                deadLetteredAt,
                Objects.requireNonNull(at, "at")
        );
    }
}
