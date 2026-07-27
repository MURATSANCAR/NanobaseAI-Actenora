package com.nanobaseai.actenora.aiprocessing.domain.job;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable intermediate or final artifact produced by a pipeline stage.
 */
public final class ProcessingArtifact {

    private final UUID id;
    private final UUID tenantId;
    private final UUID jobId;
    private final UUID meetingOccurrenceId;
    private final String artifactType;
    private final String objectKey;
    private final String contentHash;
    private final String contentType;
    private final Long sizeBytes;
    private final String payloadJson;
    private final Instant createdAt;

    public ProcessingArtifact(
            UUID id,
            UUID tenantId,
            UUID jobId,
            UUID meetingOccurrenceId,
            String artifactType,
            String objectKey,
            String contentHash,
            String contentType,
            Long sizeBytes,
            String payloadJson,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.meetingOccurrenceId = Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        this.artifactType = requireText(artifactType, "artifactType");
        this.objectKey = blankToNull(objectKey);
        this.contentHash = blankToNull(contentHash);
        this.contentType = blankToNull(contentType);
        this.sizeBytes = sizeBytes;
        this.payloadJson = blankToNull(payloadJson);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static ProcessingArtifact inlineJson(
            UUID tenantId,
            UUID jobId,
            UUID meetingOccurrenceId,
            String artifactType,
            String payloadJson,
            Instant createdAt
    ) {
        return new ProcessingArtifact(
                UUID.randomUUID(),
                tenantId,
                jobId,
                meetingOccurrenceId,
                artifactType,
                null,
                null,
                "application/json",
                payloadJson == null ? null : (long) payloadJson.length(),
                payloadJson,
                createdAt
        );
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID jobId() {
        return jobId;
    }

    public UUID meetingOccurrenceId() {
        return meetingOccurrenceId;
    }

    public String artifactType() {
        return artifactType;
    }

    public Optional<String> objectKey() {
        return Optional.ofNullable(objectKey);
    }

    public Optional<String> contentHash() {
        return Optional.ofNullable(contentHash);
    }

    public Optional<String> contentType() {
        return Optional.ofNullable(contentType);
    }

    public Optional<Long> sizeBytes() {
        return Optional.ofNullable(sizeBytes);
    }

    public Optional<String> payloadJson() {
        return Optional.ofNullable(payloadJson);
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
