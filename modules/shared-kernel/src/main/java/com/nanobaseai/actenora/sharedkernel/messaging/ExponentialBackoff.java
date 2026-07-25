package com.nanobaseai.actenora.sharedkernel.messaging;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff with full jitter: {@code random_between(0, min(cap, base * 2^attempt))}.
 */
public final class ExponentialBackoff {

    private final Duration base;
    private final Duration cap;
    private final double jitterFactor;

    public ExponentialBackoff(Duration base, Duration cap) {
        this(base, cap, 1.0);
    }

    public ExponentialBackoff(Duration base, Duration cap, double jitterFactor) {
        this.base = Objects.requireNonNull(base, "base");
        this.cap = Objects.requireNonNull(cap, "cap");
        if (base.isNegative() || base.isZero()) {
            throw new IllegalArgumentException("base must be positive");
        }
        if (cap.compareTo(base) < 0) {
            throw new IllegalArgumentException("cap must be >= base");
        }
        if (jitterFactor < 0.0 || jitterFactor > 1.0) {
            throw new IllegalArgumentException("jitterFactor must be in [0,1]");
        }
        this.jitterFactor = jitterFactor;
    }

    public static ExponentialBackoff defaults() {
        return new ExponentialBackoff(Duration.ofSeconds(1), Duration.ofMinutes(5));
    }

    public Duration delayForAttempt(int attemptCount) {
        int attempt = Math.max(0, attemptCount);
        long expMillis = Math.multiplyExact(base.toMillis(), 1L << Math.min(attempt, 20));
        long capped = Math.min(cap.toMillis(), expMillis);
        if (jitterFactor == 0.0) {
            return Duration.ofMillis(capped);
        }
        long floor = (long) (capped * (1.0 - jitterFactor));
        long span = Math.max(1L, capped - floor);
        long jittered = floor + ThreadLocalRandom.current().nextLong(span + 1);
        return Duration.ofMillis(jittered);
    }
}
