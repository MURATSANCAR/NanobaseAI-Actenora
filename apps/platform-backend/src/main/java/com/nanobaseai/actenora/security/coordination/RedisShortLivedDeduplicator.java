package com.nanobaseai.actenora.security.coordination;

import com.nanobaseai.actenora.sharedkernel.coordination.ShortLivedDeduplicator;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;

public final class RedisShortLivedDeduplicator implements ShortLivedDeduplicator {

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public RedisShortLivedDeduplicator(StringRedisTemplate redis) {
        this(redis, "actenora:dedup:");
    }

    public RedisShortLivedDeduplicator(StringRedisTemplate redis, String keyPrefix) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
    }

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
        Boolean ok = redis.opsForValue().setIfAbsent(keyPrefix + key, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }
}
