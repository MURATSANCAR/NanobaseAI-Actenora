package com.nanobaseai.actenora.aiprocessing.application.modelworker;

import java.time.Instant;
import java.util.Objects;

/**
 * Worker heartbeat snapshot for control-plane reporting.
 */
public record WorkerHeartbeat(
        String workerId,
        Instant at,
        boolean draining,
        int inFlight,
        int maxConcurrency,
        HealthStatus lastHealthStatus
) {
    public WorkerHeartbeat {
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(lastHealthStatus, "lastHealthStatus");
        if (inFlight < 0 || maxConcurrency < 1) {
            throw new IllegalArgumentException("invalid concurrency counters");
        }
    }
}
