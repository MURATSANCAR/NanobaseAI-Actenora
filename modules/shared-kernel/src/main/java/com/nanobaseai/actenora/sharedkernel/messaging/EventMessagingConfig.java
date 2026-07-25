package com.nanobaseai.actenora.sharedkernel.messaging;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Runtime configuration for the event backbone.
 */
public final class EventMessagingConfig {

    public static final int DEFAULT_MAX_PAYLOAD_BYTES = 256 * 1024;
    public static final int DEFAULT_MAX_ATTEMPTS = 8;
    public static final int DEFAULT_CONSUMER_CONCURRENCY = 4;
    public static final int DEFAULT_PUBLISH_BATCH_SIZE = 50;

    private final int maxPayloadBytes;
    private final int maxAttempts;
    private final int consumerConcurrency;
    private final int publishBatchSize;
    private final Duration pollInterval;
    private final ExponentialBackoff backoff;
    private final Set<Integer> supportedVersions;
    private final String producerName;

    public EventMessagingConfig(
            int maxPayloadBytes,
            int maxAttempts,
            int consumerConcurrency,
            int publishBatchSize,
            Duration pollInterval,
            ExponentialBackoff backoff,
            Set<Integer> supportedVersions,
            String producerName
    ) {
        if (maxPayloadBytes < 1) {
            throw new IllegalArgumentException("maxPayloadBytes must be >= 1");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (consumerConcurrency < 1) {
            throw new IllegalArgumentException("consumerConcurrency must be >= 1");
        }
        if (publishBatchSize < 1) {
            throw new IllegalArgumentException("publishBatchSize must be >= 1");
        }
        this.maxPayloadBytes = maxPayloadBytes;
        this.maxAttempts = maxAttempts;
        this.consumerConcurrency = consumerConcurrency;
        this.publishBatchSize = publishBatchSize;
        this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval");
        this.backoff = Objects.requireNonNull(backoff, "backoff");
        this.supportedVersions = Set.copyOf(Objects.requireNonNull(supportedVersions, "supportedVersions"));
        this.producerName = Objects.requireNonNull(producerName, "producerName");
    }

    public static EventMessagingConfig defaults(String producerName) {
        return new EventMessagingConfig(
                DEFAULT_MAX_PAYLOAD_BYTES,
                DEFAULT_MAX_ATTEMPTS,
                DEFAULT_CONSUMER_CONCURRENCY,
                DEFAULT_PUBLISH_BATCH_SIZE,
                Duration.ofMillis(200),
                ExponentialBackoff.defaults(),
                Set.of(1),
                producerName
        );
    }

    public int maxPayloadBytes() {
        return maxPayloadBytes;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public int consumerConcurrency() {
        return consumerConcurrency;
    }

    public int publishBatchSize() {
        return publishBatchSize;
    }

    public Duration pollInterval() {
        return pollInterval;
    }

    public ExponentialBackoff backoff() {
        return backoff;
    }

    public Set<Integer> supportedVersions() {
        return supportedVersions;
    }

    public String producerName() {
        return producerName;
    }

    public boolean supportsVersion(int version) {
        return supportedVersions.contains(version);
    }

    public EventMessagingConfig withConsumerConcurrency(int concurrency) {
        return new EventMessagingConfig(
                maxPayloadBytes,
                maxAttempts,
                concurrency,
                publishBatchSize,
                pollInterval,
                backoff,
                supportedVersions,
                producerName
        );
    }

    public EventMessagingConfig withMaxPayloadBytes(int bytes) {
        return new EventMessagingConfig(
                bytes,
                maxAttempts,
                consumerConcurrency,
                publishBatchSize,
                pollInterval,
                backoff,
                supportedVersions,
                producerName
        );
    }

    public EventMessagingConfig withMaxAttempts(int attempts) {
        return new EventMessagingConfig(
                maxPayloadBytes,
                attempts,
                consumerConcurrency,
                publishBatchSize,
                pollInterval,
                backoff,
                supportedVersions,
                producerName
        );
    }

    public EventMessagingConfig withSupportedVersions(Set<Integer> versions) {
        return new EventMessagingConfig(
                maxPayloadBytes,
                maxAttempts,
                consumerConcurrency,
                publishBatchSize,
                pollInterval,
                backoff,
                versions,
                producerName
        );
    }
}
