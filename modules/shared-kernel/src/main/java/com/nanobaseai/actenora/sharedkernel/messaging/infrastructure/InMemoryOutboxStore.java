package com.nanobaseai.actenora.sharedkernel.messaging.infrastructure;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxStatus;
import com.nanobaseai.actenora.sharedkernel.messaging.port.OutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory outbox for unit tests and local relay without a database.
 */
public final class InMemoryOutboxStore implements OutboxStore {

    private static final EnumSet<OutboxStatus> CLAIMABLE = EnumSet.of(
            OutboxStatus.PENDING,
            OutboxStatus.RETRY,
            OutboxStatus.PUBLISHING
    );

    private final ConcurrentHashMap<UUID, OutboxEvent> events = new ConcurrentHashMap<>();
    private final TenantFairnessTracker fairness;

    public InMemoryOutboxStore(TenantFairnessTracker fairness) {
        this.fairness = fairness;
    }

    @Override
    public void append(OutboxEvent event) {
        if (events.putIfAbsent(event.id(), copy(event)) != null) {
            throw new IllegalStateException("Duplicate outbox id: " + event.id());
        }
    }

    @Override
    public Optional<OutboxEvent> findById(UUID id) {
        return Optional.ofNullable(events.get(id)).map(InMemoryOutboxStore::copy);
    }

    @Override
    public List<OutboxEvent> claimDue(Instant now, int limit) {
        List<OutboxEvent> due = new ArrayList<>();
        for (OutboxEvent event : events.values()) {
            if (!CLAIMABLE.contains(event.status())) {
                continue;
            }
            if (event.status() != OutboxStatus.PUBLISHING && event.nextAttemptAt().isAfter(now)) {
                continue;
            }
            due.add(copy(event));
        }
        due.sort((a, b) -> a.nextAttemptAt().compareTo(b.nextAttemptAt()));
        List<OutboxEvent> selected = fairness.selectFair(due, limit, OutboxEvent::tenantId);
        List<OutboxEvent> claimed = new ArrayList<>(selected.size());
        for (OutboxEvent selectedEvent : selected) {
            OutboxEvent live = events.get(selectedEvent.id());
            if (live == null || !CLAIMABLE.contains(live.status())) {
                continue;
            }
            live.markPublishing();
            claimed.add(copy(live));
        }
        return List.copyOf(claimed);
    }

    @Override
    public void save(OutboxEvent event) {
        events.put(event.id(), copy(event));
    }

    @Override
    public long countByStatus(OutboxStatus status) {
        return events.values().stream().filter(e -> e.status() == status).count();
    }

    @Override
    public long countByTenantAndStatus(TenantId tenantId, OutboxStatus status) {
        return events.values().stream()
                .filter(e -> e.tenantId().equals(tenantId) && e.status() == status)
                .count();
    }

    @Override
    public List<OutboxEvent> findByStatus(OutboxStatus status, int limit) {
        return events.values().stream()
                .filter(e -> e.status() == status)
                .limit(limit)
                .map(InMemoryOutboxStore::copy)
                .toList();
    }

    public Map<UUID, OutboxEvent> snapshot() {
        ConcurrentHashMap<UUID, OutboxEvent> snap = new ConcurrentHashMap<>();
        events.forEach((id, event) -> snap.put(id, copy(event)));
        return Map.copyOf(snap);
    }

    public void clear() {
        events.clear();
    }

    private static OutboxEvent copy(OutboxEvent source) {
        return new OutboxEvent(
                source.id(),
                source.aggregateType(),
                source.aggregateId(),
                source.tenantId(),
                source.eventType(),
                source.eventVersion(),
                source.payloadJson(),
                source.correlationId(),
                source.causationId().orElse(null),
                source.traceId().orElse(null),
                source.occurredAt(),
                source.publishedAt().orElse(null),
                source.status(),
                source.attemptCount(),
                source.nextAttemptAt(),
                source.failureCode().orElse(null)
        );
    }
}
