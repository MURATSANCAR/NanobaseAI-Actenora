package com.nanobaseai.actenora.delivery.api;

import com.nanobaseai.actenora.delivery.domain.DeliveryStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeliveryRequestStatusView(
        UUID id,
        UUID noteVersionId,
        String intent,
        DeliveryStatus status,
        String recipientEmail,
        Instant createdAt,
        Instant updatedAt
) {
    public DeliveryRequestStatusView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(noteVersionId, "noteVersionId");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(recipientEmail, "recipientEmail");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
