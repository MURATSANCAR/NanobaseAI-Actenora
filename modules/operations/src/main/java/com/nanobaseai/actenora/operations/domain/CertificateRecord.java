package com.nanobaseai.actenora.operations.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * TLS / signing certificate tracked for expiry alerts.
 */
public record CertificateRecord(
        String name,
        Instant expiresAt,
        String subject
) {
    public CertificateRecord {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(subject, "subject");
    }

    public boolean expiresWithin(Instant now, java.time.Duration window) {
        return !expiresAt.isAfter(now.plus(window));
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
