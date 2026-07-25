package com.nanobaseai.actenora.operations.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Model pool member for the model pool dashboard.
 */
public record ModelPoolMember(
        String modelKey,
        String deploymentKey,
        String status,
        boolean acceptingNewWork,
        boolean heartbeatTimedOut,
        Instant lastHeartbeatAt,
        int inFlight
) {
    public ModelPoolMember {
        Objects.requireNonNull(modelKey, "modelKey");
        Objects.requireNonNull(deploymentKey, "deploymentKey");
        Objects.requireNonNull(status, "status");
        if (inFlight < 0) {
            throw new IllegalArgumentException("inFlight must be non-negative");
        }
    }

    public boolean healthy() {
        return acceptingNewWork && !heartbeatTimedOut;
    }
}
