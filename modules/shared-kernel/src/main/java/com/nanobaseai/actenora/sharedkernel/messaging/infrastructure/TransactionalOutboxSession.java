package com.nanobaseai.actenora.sharedkernel.messaging.infrastructure;

import com.nanobaseai.actenora.sharedkernel.messaging.OutboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Simulates a DB transaction boundary around outbox appends for rollback tests.
 */
public final class TransactionalOutboxSession {

    private final OutboxStore store;
    private final List<OutboxEvent> staged = new ArrayList<>();
    private boolean completed;

    public TransactionalOutboxSession(OutboxStore store) {
        this.store = store;
    }

    public void stage(OutboxEvent event) {
        ensureOpen();
        staged.add(event);
    }

    public void commit() {
        ensureOpen();
        for (OutboxEvent event : staged) {
            store.append(event);
        }
        staged.clear();
        completed = true;
    }

    public void rollback() {
        ensureOpen();
        staged.clear();
        completed = true;
    }

    public boolean isCompleted() {
        return completed;
    }

    private void ensureOpen() {
        if (completed) {
            throw new IllegalStateException("Transaction already completed");
        }
    }
}
