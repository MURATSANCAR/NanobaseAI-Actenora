package com.nanobaseai.actenora.sharedkernel.coordination;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local lock for tests and single-replica local runs.
 */
public final class InMemoryDistributedLock implements DistributedLock {

    private final ConcurrentHashMap<String, Entry> locks = new ConcurrentHashMap<>();

    @Override
    public Optional<String> tryAcquire(String key, Duration ttl) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(ttl, "ttl");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        Instant now = Instant.now();
        String token = UUID.randomUUID().toString();
        Entry created = new Entry(token, now.plus(ttl));
        while (true) {
            Entry existing = locks.get(key);
            if (existing != null && existing.expiresAt().isAfter(now)) {
                return Optional.empty();
            }
            if (existing == null) {
                if (locks.putIfAbsent(key, created) == null) {
                    return Optional.of(token);
                }
            } else if (locks.replace(key, existing, created)) {
                return Optional.of(token);
            }
        }
    }

    @Override
    public boolean release(String key, String token) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(token, "token");
        Entry existing = locks.get(key);
        if (existing == null || !existing.token().equals(token)) {
            return false;
        }
        return locks.remove(key, existing);
    }

    private record Entry(String token, Instant expiresAt) {
    }
}
