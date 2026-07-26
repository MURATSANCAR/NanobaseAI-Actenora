package com.nanobaseai.actenora.security.aiprocessing;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Local LLM runtime settings. Only local/self-hosted endpoints are supported (ADR-005).
 */
@ConfigurationProperties(prefix = "actenora.ai.provider")
public class LocalProviderProperties {

    /** mock | openai | vllm | llamacpp */
    private String kind = "mock";
    private URI baseUrl = URI.create("http://127.0.0.1:8000");
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(600);
    private int maxConcurrency = 4;
    private boolean streamingEnabled = true;
    private long degradedProbeThresholdMs = 2_000L;
    private Set<String> servedModelIds = new LinkedHashSet<>(Set.of("qwen2.5-32b-instruct", "qwen-local"));
    private int maxAttempts = 3;

    public Kind resolvedKind() {
        return Kind.from(kind);
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public boolean isStreamingEnabled() {
        return streamingEnabled;
    }

    public void setStreamingEnabled(boolean streamingEnabled) {
        this.streamingEnabled = streamingEnabled;
    }

    public long getDegradedProbeThresholdMs() {
        return degradedProbeThresholdMs;
    }

    public void setDegradedProbeThresholdMs(long degradedProbeThresholdMs) {
        this.degradedProbeThresholdMs = degradedProbeThresholdMs;
    }

    public Set<String> getServedModelIds() {
        return servedModelIds;
    }

    public void setServedModelIds(Set<String> servedModelIds) {
        this.servedModelIds = servedModelIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(servedModelIds);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public enum Kind {
        MOCK,
        OPENAI,
        VLLM,
        LLAMACPP;

        static Kind from(String value) {
            if (value == null || value.isBlank()) {
                return MOCK;
            }
            return Kind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }
}
