package com.nanobaseai.actenora.security.coordination;

import com.nanobaseai.actenora.sharedkernel.coordination.DistributedLock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis SET NX PX lock. Job state remains in PostgreSQL.
 */
public final class RedisDistributedLock implements DistributedLock {

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            end
            return 0
            """,
            Long.class
    );

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public RedisDistributedLock(StringRedisTemplate redis) {
        this(redis, "actenora:lock:");
    }

    public RedisDistributedLock(StringRedisTemplate redis, String keyPrefix) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
    }

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
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent(prefixed(key), token, ttl);
        return Boolean.TRUE.equals(ok) ? Optional.of(token) : Optional.empty();
    }

    @Override
    public boolean release(String key, String token) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(token, "token");
        Long result = redis.execute(RELEASE, List.of(prefixed(key)), token);
        return result != null && result > 0;
    }

    private String prefixed(String key) {
        return keyPrefix + key;
    }
}
