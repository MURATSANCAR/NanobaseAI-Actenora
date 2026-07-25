package com.nanobaseai.actenora.sharedkernel.messaging.infrastructure;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxStatus;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxStore;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Decorates an {@link OutboxStore} to simulate temporary PostgreSQL unavailability.
 * After {@code failNext} armed failures, operations succeed again (temporary failure).
 */
public final class TransientFailureOutboxStore implements OutboxStore {

    private final OutboxStore delegate;
    private final AtomicInteger remainingFailures = new AtomicInteger();

    public TransientFailureOutboxStore(OutboxStore delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public void armFailures(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0");
        }
        remainingFailures.set(count);
    }

    @Override
    public void append(OutboxEvent event) {
        maybeFail("append");
        delegate.append(event);
    }

    @Override
    public void save(OutboxEvent event) {
        maybeFail("save");
        delegate.save(event);
    }

    @Override
    public Optional<OutboxEvent> findById(UUID eventId) {
        maybeFail("findById");
        return delegate.findById(eventId);
    }

    @Override
    public List<OutboxEvent> claimDue(Instant now, int limit) {
        maybeFail("claimDue");
        return delegate.claimDue(now, limit);
    }

    @Override
    public long countByStatus(OutboxStatus status) {
        maybeFail("countByStatus");
        return delegate.countByStatus(status);
    }

    @Override
    public long countByTenantAndStatus(TenantId tenantId, OutboxStatus status) {
        maybeFail("countByTenantAndStatus");
        return delegate.countByTenantAndStatus(tenantId, status);
    }

    @Override
    public List<OutboxEvent> findByStatus(OutboxStatus status, int limit) {
        maybeFail("findByStatus");
        return delegate.findByStatus(status, limit);
    }

    private void maybeFail(String operation) {
        if (remainingFailures.get() > 0 && remainingFailures.getAndDecrement() > 0) {
            throw new IllegalStateException("Simulated PostgreSQL temporary failure on " + operation);
        }
    }
}
