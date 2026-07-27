package com.nanobaseai.actenora.sharedkernel.coordination;

import java.time.Duration;

/**
 * Simple fixed-window rate limit for coordination (LLM / delivery). Not durable quota accounting.
 */
public interface FixedWindowRateLimiter {

    /**
     * @return {@code true} if the call is within the window budget
     */
    boolean tryAcquire(String key, int limit, Duration window);
}
