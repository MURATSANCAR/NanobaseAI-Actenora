package com.nanobaseai.actenora.meetingintelligence.domain.artifact;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;
import java.util.UUID;

/**
 * Canonical tenant-scoped object key factory for MinIO/S3.
 * <pre>
 * tenants/{tenantId}/transcripts/{occurrenceId}/{transcriptId}/raw.vtt
 * tenants/{tenantId}/meetings/{occurrenceId}/notes/{noteId}/v{version}/draft.json
 * tenants/{tenantId}/meetings/{occurrenceId}/notes/{noteId}/v{version}/approved.json
 * tenants/{tenantId}/meetings/{occurrenceId}/extractions/{runId}/bundle.json
 * </pre>
 */
public final class TenantArtifactKeys {

    private TenantArtifactKeys() {
    }

    public static String noteDraft(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            int versionNumber
    ) {
        return noteBase(tenantId, meetingOccurrenceId, noteId, versionNumber) + "/draft.json";
    }

    public static String noteApproved(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            int versionNumber
    ) {
        return noteBase(tenantId, meetingOccurrenceId, noteId, versionNumber) + "/approved.json";
    }

    public static String extractionBundle(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID runId
    ) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        Objects.requireNonNull(runId, "runId");
        return "tenants/" + tenantId.value()
                + "/meetings/" + meetingOccurrenceId
                + "/extractions/" + runId
                + "/bundle.json";
    }

    public static void assertTenantOwnsKey(TenantId tenantId, String key) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(key, "key");
        String prefix = "tenants/" + tenantId.value() + "/";
        if (!key.startsWith(prefix)) {
            throw new IllegalArgumentException("Object key does not belong to tenant");
        }
    }

    private static String noteBase(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            int versionNumber
    ) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        Objects.requireNonNull(noteId, "noteId");
        if (versionNumber < 1) {
            throw new IllegalArgumentException("versionNumber must be >= 1");
        }
        return "tenants/" + tenantId.value()
                + "/meetings/" + meetingOccurrenceId
                + "/notes/" + noteId
                + "/v" + versionNumber;
    }
}
