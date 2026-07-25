package com.nanobaseai.actenora.sharedkernel.messaging.port;

import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;

/**
 * Broker / CDC-ready transport abstraction.
 * <p>
 * Polling publisher and future CDC relay both publish through this port so the
 * outbox store stays independent of RabbitMQ vs Kafka vs log-tail implementations.
 */
public interface EventTransport {

    /**
     * Publish envelope to the broker (or sink). Must be idempotent for the same eventId
     * when the broker supports publisher confirms / dedupe headers.
     */
    void publish(EventEnvelope envelope) throws TransportException;

    final class TransportException extends Exception {
        public TransportException(String message) {
            super(message);
        }

        public TransportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
