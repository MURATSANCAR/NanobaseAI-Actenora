package com.nanobaseai.actenora.operations.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Observed end-to-end processing latency for SLA evaluation.
 */
public record SlaObservation(
        UUID meetingId,
        TenantId tenantId,
        Instant startedAt,
        Instant completedAt,
        Duration target
) {
    public SlaObservation {
        Objects.requireNonNull(meetingId, "meetingId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(completedAt, "completedAt");
        Objects.requireNonNull(target, "target");
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt before startedAt");
        }
    }

    public Duration actual() {
        return Duration.between(startedAt, completedAt);
    }

    public boolean isBreached() {
        return actual().compareTo(target) > 0;
    }
}
