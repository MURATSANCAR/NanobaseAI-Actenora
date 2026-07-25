package com.nanobaseai.actenora.sharedkernel.messaging.port;

/**
 * Relays unpublished outbox rows to {@link EventTransport}.
 * Implementations: polling publisher now; CDC/Debezium adapter later without API change.
 */
public interface OutboxRelay extends AutoCloseable {

    /**
     * Drain one batch of due outbox events. Returns number published successfully.
     */
    int publishDueBatch();

    /**
     * Start background polling (no-op for pure pull usage).
     */
    void start();

    /**
     * Graceful stop — finish in-flight batch, reject new work.
     */
    @Override
    void close();

    boolean isRunning();
}
