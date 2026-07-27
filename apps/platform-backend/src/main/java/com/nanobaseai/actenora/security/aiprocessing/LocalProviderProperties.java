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

    /** mock | nanobaseai | openai | vllm | llamacpp — user-facing APIs expose only NanobaseAI */
    private String kind = "mock";
    private URI baseUrl = URI.create("http://127.0.0.1:8000");
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(1800);
    private int maxConcurrency = 4;
    /** Parallel chunk extraction slots (CPU-friendly default). */
    private int maxConcurrencyExtraction = 2;
    /** Merge / final / validation slots — keep at 1 on CPU. */
    private int maxConcurrencyFinal = 1;
    private boolean streamingEnabled = true;
    private long degradedProbeThresholdMs = 2_000L;
    private Set<String> servedModelIds = new LinkedHashSet<>(Set.of("nanobaseai-primary", "nanobaseai-local"));
    private int maxAttempts = 5;

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

    public int getMaxConcurrencyExtraction() {
        return maxConcurrencyExtraction;
    }

    public void setMaxConcurrencyExtraction(int maxConcurrencyExtraction) {
        this.maxConcurrencyExtraction = maxConcurrencyExtraction;
    }

    public int getMaxConcurrencyFinal() {
        return maxConcurrencyFinal;
    }

    public void setMaxConcurrencyFinal(int maxConcurrencyFinal) {
        this.maxConcurrencyFinal = maxConcurrencyFinal;
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
        NANOBASEAI,
        OPENAI,
        VLLM,
        LLAMACPP;

        static Kind from(String value) {
            if (value == null || value.isBlank()) {
                return MOCK;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            return switch (normalized) {
                case "LOCAL", "NANOBASEAI", "COMPATIBLE", "NANOBASE_AI" -> NANOBASEAI;
                case "MOCK", "OFFLINE" -> MOCK;
                case "OPENAI" -> OPENAI;
                case "VLLM" -> VLLM;
                case "LLAMACPP", "LLAMA_CPP" -> LLAMACPP;
                default -> Kind.valueOf(normalized);
            };
        }
    }
}
