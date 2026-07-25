package com.nanobaseai.actenora.operations.domain.retention;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Candidate scheduled for retention deletion once retain-until has passed. */
public record RetentionCandidate(
        TenantId tenantId,
        RetentionResourceType resourceType,
        String resourceId,
        Instant retainUntil,
        String storageKey
) {
    public RetentionCandidate {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(retainUntil, "retainUntil");
    }

    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now");
        return !retainUntil.isAfter(now);
    }

    public static RetentionCandidate transcript(
            TenantId tenantId,
            UUID transcriptId,
            Instant retainUntil,
            String storageKey
    ) {
        return new RetentionCandidate(
                tenantId,
                RetentionResourceType.TRANSCRIPT,
                transcriptId.toString(),
                retainUntil,
                storageKey);
    }

    public static RetentionCandidate privateNote(
            TenantId tenantId,
            UUID noteId,
            Instant retainUntil
    ) {
        return new RetentionCandidate(
                tenantId,
                RetentionResourceType.PRIVATE_NOTE,
                noteId.toString(),
                retainUntil,
                null);
    }
}
