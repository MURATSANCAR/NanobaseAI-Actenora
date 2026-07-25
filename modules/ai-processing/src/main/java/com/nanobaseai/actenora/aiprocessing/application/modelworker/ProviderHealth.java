package com.nanobaseai.actenora.aiprocessing.application.modelworker;

import java.time.Instant;
import java.util.Objects;

/**
 * Result of a provider health probe. Safe detail only — no response bodies.
 */
public record ProviderHealth(
        HealthStatus status,
        String detail,
        Instant probedAt,
        long probeLatencyMs
) {
    public ProviderHealth {
        Objects.requireNonNull(status, "status");
        detail = detail == null ? "" : detail;
        Objects.requireNonNull(probedAt, "probedAt");
        if (probeLatencyMs < 0) {
            throw new IllegalArgumentException("probeLatencyMs must be >= 0");
        }
    }

    public boolean acceptsWork() {
        return status == HealthStatus.UP;
    }

    public static ProviderHealth up(String detail, long probeLatencyMs) {
        return new ProviderHealth(HealthStatus.UP, detail, Instant.now(), probeLatencyMs);
    }

    public static ProviderHealth degraded(String detail, long probeLatencyMs) {
        return new ProviderHealth(HealthStatus.DEGRADED, detail, Instant.now(), probeLatencyMs);
    }

    public static ProviderHealth down(String detail, long probeLatencyMs) {
        return new ProviderHealth(HealthStatus.DOWN, detail, Instant.now(), probeLatencyMs);
    }
}
