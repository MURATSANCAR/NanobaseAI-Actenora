package com.nanobaseai.actenora.aiprocessing.infrastructure.llm;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Shared HTTP/runtime configuration for OpenAI-compatible local providers.
 * Connect and read timeouts are intentionally separate.
 */
public record LocalProviderConfig(
        String providerKind,
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxConcurrency,
        boolean streamingEnabled,
        long degradedProbeThresholdMs,
        Set<String> knownServedModelIds
) {
    public LocalProviderConfig {
        Objects.requireNonNull(providerKind, "providerKind");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(readTimeout, "readTimeout");
        if (connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException("connectTimeout must be positive");
        }
        if (readTimeout.isZero() || readTimeout.isNegative()) {
            throw new IllegalArgumentException("readTimeout must be positive");
        }
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency must be >= 1");
        }
        if (degradedProbeThresholdMs < 1) {
            throw new IllegalArgumentException("degradedProbeThresholdMs must be >= 1");
        }
        knownServedModelIds = knownServedModelIds == null ? Set.of() : Set.copyOf(knownServedModelIds);
    }

    public static Builder builder(String providerKind, URI baseUrl) {
        return new Builder(providerKind, baseUrl);
    }

    public static final class Builder {
        private final String providerKind;
        private final URI baseUrl;
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(1800);
        private int maxConcurrency = 4;
        private boolean streamingEnabled = true;
        private long degradedProbeThresholdMs = 2_000L;
        private Set<String> knownServedModelIds = Set.of();

        private Builder(String providerKind, URI baseUrl) {
            this.providerKind = providerKind;
            this.baseUrl = baseUrl;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder maxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        public Builder streamingEnabled(boolean streamingEnabled) {
            this.streamingEnabled = streamingEnabled;
            return this;
        }

        public Builder degradedProbeThresholdMs(long degradedProbeThresholdMs) {
            this.degradedProbeThresholdMs = degradedProbeThresholdMs;
            return this;
        }

        public Builder knownServedModelIds(Set<String> knownServedModelIds) {
            this.knownServedModelIds = knownServedModelIds;
            return this;
        }

        public LocalProviderConfig build() {
            return new LocalProviderConfig(
                    providerKind,
                    baseUrl,
                    connectTimeout,
                    readTimeout,
                    maxConcurrency,
                    streamingEnabled,
                    degradedProbeThresholdMs,
                    knownServedModelIds
            );
        }
    }
}
