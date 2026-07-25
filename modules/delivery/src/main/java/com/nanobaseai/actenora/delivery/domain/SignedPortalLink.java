package com.nanobaseai.actenora.delivery.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * Time-limited signed portal URL for sensitive meetings (no PDF body in mail).
 */
public record SignedPortalLink(URI url, Instant expiresAt, String tokenFingerprint) {

    public SignedPortalLink {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(tokenFingerprint, "tokenFingerprint");
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
