package com.nanobaseai.actenora.security.coordination;

import com.nanobaseai.actenora.sharedkernel.coordination.DistributedLock;
import com.nanobaseai.actenora.sharedkernel.coordination.FixedWindowRateLimiter;
import com.nanobaseai.actenora.sharedkernel.coordination.InMemoryDistributedLock;
import com.nanobaseai.actenora.sharedkernel.coordination.InMemoryFixedWindowRateLimiter;
import com.nanobaseai.actenora.sharedkernel.coordination.InMemoryJobProgressCache;
import com.nanobaseai.actenora.sharedkernel.coordination.InMemoryShortLivedDeduplicator;
import com.nanobaseai.actenora.sharedkernel.coordination.JobProgressCache;
import com.nanobaseai.actenora.sharedkernel.coordination.ShortLivedDeduplicator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis coordination adapters when enabled; otherwise in-memory fallbacks for local/tests.
 */
@Configuration
public class RedisCoordinationConfiguration {

    @Configuration
    @ConditionalOnProperty(name = "actenora.redis.coordination.enabled", havingValue = "true")
    static class RedisEnabled {
        @Bean
        DistributedLock distributedLock(StringRedisTemplate redis) {
            return new RedisDistributedLock(redis);
        }

        @Bean
        ShortLivedDeduplicator shortLivedDeduplicator(StringRedisTemplate redis) {
            return new RedisShortLivedDeduplicator(redis);
        }

        @Bean
        FixedWindowRateLimiter fixedWindowRateLimiter(StringRedisTemplate redis) {
            return new RedisFixedWindowRateLimiter(redis);
        }

        @Bean
        JobProgressCache jobProgressCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
            return new RedisJobProgressCache(redis, objectMapper);
        }
    }

    @Bean
    @ConditionalOnMissingBean(DistributedLock.class)
    DistributedLock inMemoryDistributedLock() {
        return new InMemoryDistributedLock();
    }

    @Bean
    @ConditionalOnMissingBean(ShortLivedDeduplicator.class)
    ShortLivedDeduplicator inMemoryShortLivedDeduplicator() {
        return new InMemoryShortLivedDeduplicator();
    }

    @Bean
    @ConditionalOnMissingBean(FixedWindowRateLimiter.class)
    FixedWindowRateLimiter inMemoryFixedWindowRateLimiter() {
        return new InMemoryFixedWindowRateLimiter();
    }

    @Bean
    @ConditionalOnMissingBean(JobProgressCache.class)
    JobProgressCache inMemoryJobProgressCache() {
        return new InMemoryJobProgressCache();
    }
}
