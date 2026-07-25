package com.nanobaseai.actenora.sharedkernel.messaging.support;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Measures and enforces tenant fairness when claiming outbox work.
 * Strategy: round-robin across tenants that have due work (starvation-safe).
 */
public final class TenantFairnessTracker {

    private final ConcurrentHashMap<TenantId, AtomicLong> publishedByTenant = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TenantId, AtomicLong> claimedByTenant = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<TenantId, AtomicLong> deadLetterByTenant = new ConcurrentHashMap<>();

    public void recordClaim(TenantId tenantId) {
        claimedByTenant.computeIfAbsent(tenantId, ignored -> new AtomicLong()).incrementAndGet();
    }

    public void recordPublished(TenantId tenantId) {
        publishedByTenant.computeIfAbsent(tenantId, ignored -> new AtomicLong()).incrementAndGet();
    }

    public void recordDeadLetter(TenantId tenantId) {
        deadLetterByTenant.computeIfAbsent(tenantId, ignored -> new AtomicLong()).incrementAndGet();
    }

    public long claimed(TenantId tenantId) {
        AtomicLong v = claimedByTenant.get(tenantId);
        return v == null ? 0L : v.get();
    }

    public long published(TenantId tenantId) {
        AtomicLong v = publishedByTenant.get(tenantId);
        return v == null ? 0L : v.get();
    }

    public Map<TenantId, Long> snapshotPublished() {
        LinkedHashMap<TenantId, Long> snap = new LinkedHashMap<>();
        publishedByTenant.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().value().toString()))
                .forEach(e -> snap.put(e.getKey(), e.getValue().get()));
        return Map.copyOf(snap);
    }

    /**
     * Fair selection: pick at most {@code limit} items, spreading across tenants.
     */
    public <T> List<T> selectFair(List<T> due, int limit, Function<T, TenantId> tenantOf) {
        Objects.requireNonNull(due, "due");
        Objects.requireNonNull(tenantOf, "tenantOf");
        if (limit < 1 || due.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<TenantId, List<T>> byTenant = new LinkedHashMap<>();
        for (T item : due) {
            TenantId tenantId = tenantOf.apply(item);
            byTenant.computeIfAbsent(tenantId, ignored -> new ArrayList<>()).add(item);
        }

        List<TenantId> order = new ArrayList<>(byTenant.keySet());
        order.sort(Comparator.comparing(t -> claimed(t)));

        List<T> selected = new ArrayList<>(Math.min(limit, due.size()));
        boolean progress = true;
        while (selected.size() < limit && progress) {
            progress = false;
            for (TenantId tenantId : order) {
                List<T> bucket = byTenant.get(tenantId);
                if (bucket == null || bucket.isEmpty()) {
                    continue;
                }
                T next = bucket.removeFirst();
                selected.add(next);
                recordClaim(tenantId);
                progress = true;
                if (selected.size() >= limit) {
                    break;
                }
            }
        }
        return List.copyOf(selected);
    }
}
