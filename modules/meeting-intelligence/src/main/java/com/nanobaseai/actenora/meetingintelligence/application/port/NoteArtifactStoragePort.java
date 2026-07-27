package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.UUID;

/**
 * Persists note/extraction JSON artifacts to object storage under TenantArtifactKeys.
 */
public interface NoteArtifactStoragePort {

    void storeDraft(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            int versionNumber,
            String contentJson
    );

    void storeApproved(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            int versionNumber,
            String contentJson
    );

    void storeExtractionBundle(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID runId,
            String contentJson
    );

    static NoteArtifactStoragePort noop() {
        return new NoteArtifactStoragePort() {
            @Override
            public void storeDraft(TenantId tenantId, UUID meetingOccurrenceId, UUID noteId, int versionNumber, String contentJson) {
            }

            @Override
            public void storeApproved(TenantId tenantId, UUID meetingOccurrenceId, UUID noteId, int versionNumber, String contentJson) {
            }

            @Override
            public void storeExtractionBundle(TenantId tenantId, UUID meetingOccurrenceId, UUID runId, String contentJson) {
            }
        };
    }
}
