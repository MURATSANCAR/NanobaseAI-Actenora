package com.nanobaseai.actenora.sharedkernel.port.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * Short-lived authorized download URL.
 */
public record AuthorizedUrl(URI url, Instant expiresAt) {

    public AuthorizedUrl {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
