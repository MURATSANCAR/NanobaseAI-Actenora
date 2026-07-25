package com.nanobaseai.actenora.sharedkernel.port.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * Short-lived authorized download URL.
 */
public record AuthorizedUrl(URI url, Instant expiresAt) {

    public static final long DEFAULT_MAX_TTL_SECONDS = 900L;

    public AuthorizedUrl {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now");
        return !expiresAt.isAfter(now);
    }
}
