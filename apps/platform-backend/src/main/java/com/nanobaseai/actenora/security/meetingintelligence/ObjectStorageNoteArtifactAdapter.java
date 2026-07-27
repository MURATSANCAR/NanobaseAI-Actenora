package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.meetingintelligence.application.port.NoteArtifactStoragePort;
import com.nanobaseai.actenora.meetingintelligence.domain.artifact.TenantArtifactKeys;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectPutRequest;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Writes note/extraction artifacts to MinIO/S3 using canonical TenantArtifactKeys.
 */
public final class ObjectStorageNoteArtifactAdapter implements NoteArtifactStoragePort {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageNoteArtifactAdapter.class);

    private final ObjectStorage objectStorage;

    public ObjectStorageNoteArtifactAdapter(ObjectStorage objectStorage) {
        this.objectStorage = Objects.requireNonNull(objectStorage, "objectStorage");
    }

    @Override
    public void storeDraft(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            int versionNumber,
            String contentJson
    ) {
        put(TenantArtifactKeys.noteDraft(tenantId, meetingOccurrenceId, noteId, versionNumber), contentJson);
    }

    @Override
    public void storeApproved(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            int versionNumber,
            String contentJson
    ) {
        put(TenantArtifactKeys.noteApproved(tenantId, meetingOccurrenceId, noteId, versionNumber), contentJson);
    }

    @Override
    public void storeExtractionBundle(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID runId,
            String contentJson
    ) {
        put(TenantArtifactKeys.extractionBundle(tenantId, meetingOccurrenceId, runId), contentJson);
    }

    private void put(String key, String contentJson) {
        try {
            byte[] bytes = (contentJson == null ? "{}" : contentJson).getBytes(StandardCharsets.UTF_8);
            objectStorage.put(ObjectPutRequest.builder()
                    .key(key)
                    .content(new java.io.ByteArrayInputStream(bytes))
                    .contentLength(bytes.length)
                    .contentType("application/json")
                    .immutable(true)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Note artifact put failed key={} reason={}", key, ex.getMessage());
        }
    }
}
