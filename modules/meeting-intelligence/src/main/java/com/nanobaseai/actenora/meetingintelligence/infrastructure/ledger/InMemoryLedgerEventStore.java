package com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger;

import com.nanobaseai.actenora.meetingintelligence.application.ledger.port.LedgerEventStore;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.event.LedgerEvent;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryLedgerEventStore implements LedgerEventStore {

    private final Map<TenantId, List<LedgerEvent>> events = new ConcurrentHashMap<>();
    private final Map<TenantId, AtomicLong> sequences = new ConcurrentHashMap<>();

    @Override
    public LedgerEvent append(LedgerEvent event) {
        events.computeIfAbsent(event.tenantId(), ignored -> new ArrayList<>()).add(event);
        return event;
    }

    @Override
    public List<LedgerEvent> findAllByTenant(TenantId tenantId) {
        return events.getOrDefault(tenantId, List.of()).stream()
                .sorted(Comparator.comparingLong(LedgerEvent::sequence))
                .toList();
    }

    @Override
    public long nextSequence(TenantId tenantId) {
        return sequences.computeIfAbsent(tenantId, ignored -> new AtomicLong(0)).incrementAndGet();
    }
}
