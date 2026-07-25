package com.nanobaseai.actenora.delivery.infrastructure.ratelimit;

import com.nanobaseai.actenora.delivery.application.port.DeliveryRateLimiter;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window rate limiter: max {@code limitPerMinute} acquires per tenant+provider per minute.
 */
public final class FixedWindowDeliveryRateLimiter implements DeliveryRateLimiter {

    private final int limitPerMinute;
    private final InstantClock clock;
    private final Map<String, Deque<Instant>> windows = new ConcurrentHashMap<>();

    public FixedWindowDeliveryRateLimiter(int limitPerMinute, InstantClock clock) {
        if (limitPerMinute < 1) {
            throw new IllegalArgumentException("limitPerMinute must be >= 1");
        }
        this.limitPerMinute = limitPerMinute;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean tryAcquire(TenantId tenantId, String providerType) {
        String key = key(tenantId, providerType);
        Instant now = clock.now();
        Instant cutoff = now.minus(Duration.ofMinutes(1));
        synchronized (windows) {
            Deque<Instant> q = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
            while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) {
                q.removeFirst();
            }
            if (q.size() >= limitPerMinute) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }

    @Override
    public void release(TenantId tenantId, String providerType) {
        // fixed-window: no-op release (slot already consumed for the minute)
    }

    public int currentCount(TenantId tenantId, String providerType) {
        String key = key(tenantId, providerType);
        Instant now = clock.now();
        Instant cutoff = now.minus(Duration.ofMinutes(1));
        synchronized (windows) {
            Deque<Instant> q = windows.getOrDefault(key, new ArrayDeque<>());
            return (int) q.stream().filter(t -> !t.isBefore(cutoff)).count();
        }
    }

    private static String key(TenantId tenantId, String providerType) {
        return tenantId.value() + "|" + providerType;
    }
}
