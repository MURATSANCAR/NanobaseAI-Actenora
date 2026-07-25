package com.nanobaseai.actenora.operations.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Worker liveness snapshot for the ops health panel.
 */
public record WorkerHealth(
        String workerId,
        String role,
        boolean draining,
        int inFlight,
        int maxConcurrency,
        Instant lastHeartbeatAt,
        String lastHealthStatus
) {
    public WorkerHealth {
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(lastHeartbeatAt, "lastHeartbeatAt");
        Objects.requireNonNull(lastHealthStatus, "lastHealthStatus");
        if (inFlight < 0 || maxConcurrency < 0) {
            throw new IllegalArgumentException("concurrency must be non-negative");
        }
    }

    public boolean isStale(Instant now, java.time.Duration maxAge) {
        return lastHeartbeatAt.isBefore(now.minus(maxAge));
    }
}
