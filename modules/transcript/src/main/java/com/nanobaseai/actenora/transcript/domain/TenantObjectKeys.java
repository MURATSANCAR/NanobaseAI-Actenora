package com.nanobaseai.actenora.transcript.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;
import java.util.UUID;

/**
 * Tenant-scoped object key factory.
 * Pattern: tenants/{tenantId}/transcripts/{meetingOccurrenceId}/{transcriptId}/raw.vtt
 */
public final class TenantObjectKeys {

    private TenantObjectKeys() {
    }

    public static String rawVtt(TenantId tenantId, UUID meetingOccurrenceId, UUID transcriptId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        Objects.requireNonNull(transcriptId, "transcriptId");
        return "tenants/" + tenantId.value()
                + "/transcripts/" + meetingOccurrenceId
                + "/" + transcriptId
                + "/raw.vtt";
    }

    public static String normalized(TenantId tenantId, UUID meetingOccurrenceId, UUID transcriptId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        Objects.requireNonNull(transcriptId, "transcriptId");
        return "tenants/" + tenantId.value()
                + "/transcripts/" + meetingOccurrenceId
                + "/" + transcriptId
                + "/normalized.json";
    }

    /** Immutable per-run normalized payload key (FAZ 9 — avoids overwrite on dictionary revision). */
    public static String normalizedRun(
            TenantId tenantId, UUID meetingOccurrenceId, UUID transcriptId, UUID runId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        Objects.requireNonNull(transcriptId, "transcriptId");
        Objects.requireNonNull(runId, "runId");
        return "tenants/" + tenantId.value()
                + "/transcripts/" + meetingOccurrenceId
                + "/" + transcriptId
                + "/normalized/" + runId + ".json";
    }

    public static void assertTenantOwnsKey(TenantId tenantId, String key) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(key, "key");
        String prefix = "tenants/" + tenantId.value() + "/";
        if (!key.startsWith(prefix)) {
            throw new TranscriptDomainException(
                    "TENANT_KEY_MISMATCH",
                    "Object key does not belong to tenant");
        }
    }
}
