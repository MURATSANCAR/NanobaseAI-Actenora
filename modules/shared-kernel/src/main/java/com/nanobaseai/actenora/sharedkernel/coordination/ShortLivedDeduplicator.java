package com.nanobaseai.actenora.sharedkernel.coordination;

import java.time.Duration;

/**
 * Short-TTL duplicate suppression for high-churn ids (e.g. Graph notification ids).
 * Durable idempotency still belongs in Postgres inbox tables.
 */
public interface ShortLivedDeduplicator {

    /**
     * @return {@code true} if this is the first sighting within TTL; {@code false} if duplicate
     */
    boolean tryClaim(String key, Duration ttl);
}
