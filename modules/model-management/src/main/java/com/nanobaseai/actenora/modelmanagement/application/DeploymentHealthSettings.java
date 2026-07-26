package com.nanobaseai.actenora.modelmanagement.application;

import java.time.Duration;
import java.util.Objects;

/**
 * Ops-tunable health policy for deployments.
 */
public record DeploymentHealthSettings(Duration heartbeatTimeout) {

    public DeploymentHealthSettings {
        Objects.requireNonNull(heartbeatTimeout, "heartbeatTimeout");
        if (heartbeatTimeout.isNegative() || heartbeatTimeout.isZero()) {
            throw new IllegalArgumentException("heartbeatTimeout must be > 0");
        }
    }

    public static DeploymentHealthSettings defaults() {
        return new DeploymentHealthSettings(Duration.ofMinutes(2));
    }
}
