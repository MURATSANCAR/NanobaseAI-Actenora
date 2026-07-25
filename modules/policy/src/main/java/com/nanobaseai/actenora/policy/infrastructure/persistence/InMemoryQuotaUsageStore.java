package com.nanobaseai.actenora.policy.infrastructure.persistence;

import com.nanobaseai.actenora.policy.application.QuotaUsagePort;
import com.nanobaseai.actenora.policy.domain.QuotaDimension;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory quota counters representing the durable usage store. */
public final class InMemoryQuotaUsageStore implements QuotaUsagePort {

    private final Map<String, AtomicLong> usage = new ConcurrentHashMap<>();
    private final Map<TenantId, AtomicInteger> concurrentAiJobs = new ConcurrentHashMap<>();

    @Override
    public long getUsage(TenantId tenantId, QuotaDimension dimension, LocalDate day) {
        return usage.computeIfAbsent(key(tenantId, dimension, day), ignored -> new AtomicLong()).get();
    }

    @Override
    public void addUsage(TenantId tenantId, QuotaDimension dimension, LocalDate day, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        usage.computeIfAbsent(key(tenantId, dimension, day), ignored -> new AtomicLong()).addAndGet(amount);
    }

    @Override
    public int getConcurrentAiJobs(TenantId tenantId) {
        return concurrentAiJobs.computeIfAbsent(tenantId, ignored -> new AtomicInteger()).get();
    }

    @Override
    public void setConcurrentAiJobs(TenantId tenantId, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative");
        }
        concurrentAiJobs.computeIfAbsent(tenantId, ignored -> new AtomicInteger()).set(count);
    }

    public void clear() {
        usage.clear();
        concurrentAiJobs.clear();
    }

    private static String key(TenantId tenantId, QuotaDimension dimension, LocalDate day) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(day, "day");
        return tenantId.value() + "|" + dimension.name() + "|" + day;
    }
}
