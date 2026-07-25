package com.nanobaseai.actenora.operations.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Visible retry row for the retry viewer (FAZ 25).
 */
public record RetryEntry(
        UUID eventId,
        String eventType,
        TenantId tenantId,
        int attemptCount,
        Instant nextAttemptAt,
        String failureCode,
        String status,
        UUID correlationId
) {
    public RetryEntry {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(correlationId, "correlationId");
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must be non-negative");
        }
    }

    public Optional<String> failureCodeOptional() {
        return Optional.ofNullable(failureCode);
    }
}
