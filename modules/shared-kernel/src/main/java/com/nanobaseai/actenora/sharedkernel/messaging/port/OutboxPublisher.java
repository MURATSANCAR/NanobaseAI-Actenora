package com.nanobaseai.actenora.sharedkernel.messaging.port;

import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxEvent;

/**
 * Application-facing transactional outbox writer.
 */
public interface OutboxPublisher {

    /**
     * Persist envelope into the outbox within the current transaction.
     * Does not talk to the broker.
     */
    OutboxEvent enqueue(EventEnvelope envelope);
}
