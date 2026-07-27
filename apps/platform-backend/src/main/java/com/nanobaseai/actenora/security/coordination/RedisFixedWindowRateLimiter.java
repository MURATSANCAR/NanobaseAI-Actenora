package com.nanobaseai.actenora.security.coordination;

import com.nanobaseai.actenora.sharedkernel.coordination.FixedWindowRateLimiter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Redis fixed-window counter. Not a substitute for durable tenant quota in PostgreSQL.
 */
public final class RedisFixedWindowRateLimiter implements FixedWindowRateLimiter {

    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>(
            """
            local current = redis.call('incr', KEYS[1])
            if current == 1 then
              redis.call('pexpire', KEYS[1], ARGV[1])
            end
            if current > tonumber(ARGV[2]) then
              return 0
            end
            return 1
            """,
            Long.class
    );

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public RedisFixedWindowRateLimiter(StringRedisTemplate redis) {
        this(redis, "actenora:rate:");
    }

    public RedisFixedWindowRateLimiter(StringRedisTemplate redis, String keyPrefix) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
    }

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
        long ttlMs = Math.max(1L, window.toMillis());
        Long allowed = redis.execute(
                ACQUIRE,
                List.of(keyPrefix + key),
                String.valueOf(ttlMs),
                String.valueOf(limit)
        );
        return allowed != null && allowed > 0;
    }
}
