package com.nanobaseai.actenora.sharedkernel.coordination;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryShortLivedDeduplicator implements ShortLivedDeduplicator {

    private final ConcurrentHashMap<String, Instant> seen = new ConcurrentHashMap<>();

    @Override
    public boolean tryClaim(String key, Duration ttl) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(ttl, "ttl");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        Instant now = Instant.now();
        Instant expires = now.plus(ttl);
        while (true) {
            Instant existing = seen.get(key);
            if (existing != null && existing.isAfter(now)) {
                return false;
            }
            if (existing == null) {
                if (seen.putIfAbsent(key, expires) == null) {
                    return true;
                }
            } else if (seen.replace(key, existing, expires)) {
                return true;
            }
        }
    }
}
