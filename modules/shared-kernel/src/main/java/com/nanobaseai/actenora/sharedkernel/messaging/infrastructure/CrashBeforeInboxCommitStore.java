package com.nanobaseai.actenora.sharedkernel.messaging.infrastructure;

import com.nanobaseai.actenora.sharedkernel.messaging.InboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.InboxStatus;
import com.nanobaseai.actenora.sharedkernel.messaging.port.InboxStore;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test double: crashes when committing PROCESSED, simulating consumer crash
 * after handler side effects but before inbox commit.
 */
public final class CrashBeforeInboxCommitStore implements InboxStore {

    private final InboxStore delegate;
    private final AtomicBoolean arm = new AtomicBoolean(false);

    public CrashBeforeInboxCommitStore(InboxStore delegate) {
        this.delegate = delegate;
    }

    public void armOnce() {
        arm.set(true);
    }

    @Override
    public ClaimResult claim(InboxEvent event) {
        return delegate.claim(event);
    }

    @Override
    public Optional<InboxEvent> find(String consumerName, UUID eventId) {
        return delegate.find(consumerName, eventId);
    }

    @Override
    public void save(InboxEvent event) {
        if (event.status() == InboxStatus.PROCESSED && arm.compareAndSet(true, false)) {
            throw new IllegalStateException("Simulated consumer crash before inbox commit");
        }
        delegate.save(event);
    }
}
