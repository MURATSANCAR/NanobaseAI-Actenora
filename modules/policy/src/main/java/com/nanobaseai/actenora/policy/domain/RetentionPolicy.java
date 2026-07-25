package com.nanobaseai.actenora.policy.domain;

/** How long tenant data is retained. */
public record RetentionPolicy(int retentionDays, boolean legalHoldAllowed) {
    public RetentionPolicy {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("retentionDays must be >= 1");
        }
    }

    public static RetentionPolicy systemDefaults() {
        return new RetentionPolicy(365, true);
    }
}
