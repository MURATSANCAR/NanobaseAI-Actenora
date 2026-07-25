package com.nanobaseai.actenora.microsoftconnection.application.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Cached Microsoft identity access token.
 */
public record AccessToken(String value, Instant expiresAt) {

    public AccessToken {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (value.isBlank()) {
            throw new IllegalArgumentException("token value must not be blank");
        }
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isExpiredOrNearExpiry(Instant now, long skewSeconds) {
        return !expiresAt.minusSeconds(Math.max(0, skewSeconds)).isAfter(now);
    }
}
