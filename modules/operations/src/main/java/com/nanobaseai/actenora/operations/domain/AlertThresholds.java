package com.nanobaseai.actenora.operations.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Configurable thresholds for ops alerts (FAZ 25).
 */
public record AlertThresholds(
        Duration certificateExpiryWarning,
        Duration slaBreachLatency,
        int dlqDepthWarning,
        int dlqDepthCritical,
        int aiQueueDepthWarning,
        long transcriptPendingAgeWarningSeconds
) {
    public AlertThresholds {
        Objects.requireNonNull(certificateExpiryWarning, "certificateExpiryWarning");
        Objects.requireNonNull(slaBreachLatency, "slaBreachLatency");
        if (certificateExpiryWarning.isNegative() || certificateExpiryWarning.isZero()) {
            throw new IllegalArgumentException("certificateExpiryWarning must be positive");
        }
        if (slaBreachLatency.isNegative() || slaBreachLatency.isZero()) {
            throw new IllegalArgumentException("slaBreachLatency must be positive");
        }
        if (dlqDepthWarning < 0 || dlqDepthCritical < dlqDepthWarning) {
            throw new IllegalArgumentException("invalid DLQ depth thresholds");
        }
        if (aiQueueDepthWarning < 0) {
            throw new IllegalArgumentException("aiQueueDepthWarning must be non-negative");
        }
        if (transcriptPendingAgeWarningSeconds < 0) {
            throw new IllegalArgumentException("transcriptPendingAgeWarningSeconds must be non-negative");
        }
    }

    public static AlertThresholds defaults() {
        return new AlertThresholds(
                Duration.ofDays(30),
                Duration.ofMinutes(60),
                1,
                10,
                100,
                900
        );
    }
}
