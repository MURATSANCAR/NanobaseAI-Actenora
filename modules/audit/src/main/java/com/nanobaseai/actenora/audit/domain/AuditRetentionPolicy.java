package com.nanobaseai.actenora.audit.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * FAZ 27 — audit retention policy (archive eligibility; application must never DELETE entries).
 */
public record AuditRetentionPolicy(int retentionDays) {

    public static final int DEFAULT_DAYS = 2555; // ~7 years

    public AuditRetentionPolicy {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("retentionDays must be >= 1");
        }
    }

    public static AuditRetentionPolicy systemDefaults() {
        return new AuditRetentionPolicy(DEFAULT_DAYS);
    }

    public Instant archiveCutoff(Instant now) {
        Objects.requireNonNull(now, "now");
        return now.minus(retentionDays, ChronoUnit.DAYS);
    }

    public boolean isEligibleForArchive(Instant occurredAt, Instant now) {
        Objects.requireNonNull(occurredAt, "occurredAt");
        return !occurredAt.isAfter(archiveCutoff(now));
    }
}
