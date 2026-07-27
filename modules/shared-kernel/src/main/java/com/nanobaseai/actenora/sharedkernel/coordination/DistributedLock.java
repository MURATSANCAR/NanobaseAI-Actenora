package com.nanobaseai.actenora.sharedkernel.coordination;

import java.time.Duration;
import java.util.Optional;

/**
 * Short-lived coordination lock. Not a source of truth for job state (Postgres owns that).
 */
public interface DistributedLock {

    /**
     * Attempts to acquire {@code key} for {@code ttl}. Returns a token on success.
     * Caller must {@link #release(String, String)} with the same token.
     */
    Optional<String> tryAcquire(String key, Duration ttl);

    /**
     * Releases only if {@code token} still owns the lock (safe against expiry races).
     */
    boolean release(String key, String token);
}
