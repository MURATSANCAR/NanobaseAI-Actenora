package com.nanobaseai.actenora.sharedkernel.coordination;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryCoordinationTest {

    @Test
    void distributedLockIsExclusiveUntilReleased() {
        DistributedLock lock = new InMemoryDistributedLock();
        var first = lock.tryAcquire("meeting:1:processing", Duration.ofSeconds(5));
        assertTrue(first.isPresent());
        assertTrue(lock.tryAcquire("meeting:1:processing", Duration.ofSeconds(5)).isEmpty());
        assertTrue(lock.release("meeting:1:processing", first.get()));
        assertTrue(lock.tryAcquire("meeting:1:processing", Duration.ofSeconds(5)).isPresent());
    }

    @Test
    void deduplicatorClaimsOncePerTtl() {
        ShortLivedDeduplicator dedup = new InMemoryShortLivedDeduplicator();
        assertTrue(dedup.tryClaim("graph:n1", Duration.ofSeconds(10)));
        assertFalse(dedup.tryClaim("graph:n1", Duration.ofSeconds(10)));
    }

    @Test
    void rateLimiterRespectsWindowBudget() {
        FixedWindowRateLimiter limiter = new InMemoryFixedWindowRateLimiter();
        assertTrue(limiter.tryAcquire("tenant:t1:llm", 2, Duration.ofMinutes(1)));
        assertTrue(limiter.tryAcquire("tenant:t1:llm", 2, Duration.ofMinutes(1)));
        assertFalse(limiter.tryAcquire("tenant:t1:llm", 2, Duration.ofMinutes(1)));
    }
}
