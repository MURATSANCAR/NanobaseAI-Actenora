package com.nanobaseai.actenora.meetingintelligence.domain.artifact;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ArtifactMetadata(
        UUID id,
        TenantId tenantId,
        Optional<UUID> meetingOccurrenceId,
        Optional<UUID> noteId,
        Optional<UUID> noteVersionId,
        ArtifactKind artifactKind,
        String storageKey,
        String contentType,
        Optional<Long> contentLengthBytes,
        Optional<String> checksumSha256,
        Instant createdAt
) {
    public ArtifactMetadata {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        meetingOccurrenceId = meetingOccurrenceId == null ? Optional.empty() : meetingOccurrenceId;
        noteId = noteId == null ? Optional.empty() : noteId;
        noteVersionId = noteVersionId == null ? Optional.empty() : noteVersionId;
        Objects.requireNonNull(artifactKind, "artifactKind");
        Objects.requireNonNull(storageKey, "storageKey");
        if (storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey is required");
        }
        Objects.requireNonNull(contentType, "contentType");
        contentLengthBytes = contentLengthBytes == null ? Optional.empty() : contentLengthBytes;
        checksumSha256 = checksumSha256 == null ? Optional.empty() : checksumSha256;
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
