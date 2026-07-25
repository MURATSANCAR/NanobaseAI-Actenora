package com.nanobaseai.actenora.sharedkernel.messaging.outbox;

import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.EventSchemaValidator;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxPublisher;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxStore;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

import java.util.Objects;

/**
 * Writes events to the outbox in the caller's transaction — never publishes to the broker.
 */
public final class TransactionalOutboxPublisher implements OutboxPublisher {

    private final OutboxStore store;
    private final EventSchemaValidator validator;
    private final InstantClock clock;

    public TransactionalOutboxPublisher(
            OutboxStore store,
            EventSchemaValidator validator,
            InstantClock clock
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public OutboxEvent enqueue(EventEnvelope envelope) {
        validator.validateForPublish(envelope);
        OutboxEvent event = OutboxEvent.pending(envelope, clock.now());
        store.append(event);
        return event;
    }
}
