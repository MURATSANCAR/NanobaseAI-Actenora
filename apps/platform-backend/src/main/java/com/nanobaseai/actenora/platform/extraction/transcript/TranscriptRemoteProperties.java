package com.nanobaseai.actenora.platform.extraction.transcript;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Remote transcript-worker client settings (FAZ 26).
 * Used when {@code actenora.transcript.mode=remote}.
 */
@ConfigurationProperties(prefix = "actenora.transcript.remote")
public class TranscriptRemoteProperties {

    /**
     * Base URL of the extracted transcript-worker (no trailing slash).
     */
    private String baseUrl = "http://localhost:8081";

    private Duration connectTimeout = Duration.ofSeconds(2);

    private Duration readTimeout = Duration.ofSeconds(30);

    private int maxRetries = 3;

    private Duration retryBackoff = Duration.ofMillis(200);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
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

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Duration getRetryBackoff() {
        return retryBackoff;
    }

    public void setRetryBackoff(Duration retryBackoff) {
        this.retryBackoff = retryBackoff;
    }
}
