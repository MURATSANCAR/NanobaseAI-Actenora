package com.nanobaseai.actenora.security.coordination;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.sharedkernel.coordination.JobProgressCache;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RedisJobProgressCache implements JobProgressCache {

    private static final Duration TTL = Duration.ofHours(2);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;

    public RedisJobProgressCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this(redis, objectMapper, "actenora:progress:meeting:");
    }

    public RedisJobProgressCache(StringRedisTemplate redis, ObjectMapper objectMapper, String keyPrefix) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
    }

    @Override
    public void put(UUID meetingOccurrenceId, Progress progress) {
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        Objects.requireNonNull(progress, "progress");
        try {
            String json = objectMapper.writeValueAsString(progress);
            redis.opsForValue().set(keyPrefix + meetingOccurrenceId, json, TTL);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write meeting progress cache", ex);
        }
    }

    @Override
    public Optional<Progress> get(UUID meetingOccurrenceId) {
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        String json = redis.opsForValue().get(keyPrefix + meetingOccurrenceId);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, Progress.class));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
