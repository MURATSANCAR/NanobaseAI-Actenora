package com.nanobaseai.actenora.sharedkernel.messaging.infrastructure;

import com.nanobaseai.actenora.sharedkernel.messaging.OutboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxStatus;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxStore;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Test double: throws after a successful broker publish when persisting PUBLISHED,
 * simulating process crash between broker ack and outbox commit.
 */
public final class CrashAfterPublishOutboxStore implements OutboxStore {

    private final OutboxStore delegate;
    private final AtomicBoolean arm = new AtomicBoolean(false);
    private Predicate<OutboxEvent> match = event -> true;

    public CrashAfterPublishOutboxStore(OutboxStore delegate) {
        this.delegate = delegate;
    }

    public void armOnce() {
        arm.set(true);
    }

    public void armOnceWhen(Predicate<OutboxEvent> match) {
        this.match = match;
        arm.set(true);
    }

    @Override
    public void append(OutboxEvent event) {
        delegate.append(event);
    }

    @Override
    public java.util.Optional<OutboxEvent> findById(java.util.UUID id) {
        return delegate.findById(id);
    }

    @Override
    public java.util.List<OutboxEvent> claimDue(java.time.Instant now, int limit) {
        return delegate.claimDue(now, limit);
    }

    @Override
    public void save(OutboxEvent event) {
        if (event.status() == OutboxStatus.PUBLISHED
                && arm.compareAndSet(true, false)
                && match.test(event)) {
            throw new IllegalStateException("Simulated publisher crash after broker publish");
        }
        delegate.save(event);
    }

    @Override
    public long countByStatus(OutboxStatus status) {
        return delegate.countByStatus(status);
    }

    @Override
    public long countByTenantAndStatus(
            com.nanobaseai.actenora.sharedkernel.domain.TenantId tenantId,
            OutboxStatus status
    ) {
        return delegate.countByTenantAndStatus(tenantId, status);
    }

    @Override
    public java.util.List<OutboxEvent> findByStatus(OutboxStatus status, int limit) {
        return delegate.findByStatus(status, limit);
    }
}
