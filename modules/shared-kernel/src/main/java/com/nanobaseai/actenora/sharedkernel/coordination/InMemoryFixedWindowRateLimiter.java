package com.nanobaseai.actenora.sharedkernel.coordination;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryFixedWindowRateLimiter implements FixedWindowRateLimiter {

    private final ConcurrentHashMap<String, Deque<Instant>> windows = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(window, "window");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);
        Deque<Instant> stamps = windows.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (stamps) {
            while (!stamps.isEmpty() && stamps.peekFirst().isBefore(cutoff)) {
                stamps.removeFirst();
            }
            if (stamps.size() >= limit) {
                return false;
            }
            stamps.addLast(now);
            return true;
        }
    }
}
