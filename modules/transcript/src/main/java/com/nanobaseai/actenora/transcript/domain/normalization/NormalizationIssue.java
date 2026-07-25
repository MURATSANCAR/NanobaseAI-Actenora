package com.nanobaseai.actenora.transcript.domain.normalization;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Non-fatal or fatal diagnostic attached to a normalization run.
 */
public final class NormalizationIssue {

    private final UUID id;
    private final NormalizationIssueType type;
    private final String message;
    private final Integer sequence;
    private final UUID originalSegmentId;
    private final boolean blocking;

    public NormalizationIssue(
            UUID id,
            NormalizationIssueType type,
            String message,
            Integer sequence,
            UUID originalSegmentId,
            boolean blocking) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.message = Objects.requireNonNull(message, "message");
        this.sequence = sequence;
        this.originalSegmentId = originalSegmentId;
        this.blocking = blocking;
    }

    public static NormalizationIssue of(
            NormalizationIssueType type,
            String message,
            Integer sequence,
            UUID originalSegmentId,
            boolean blocking) {
        return new NormalizationIssue(
                UUID.randomUUID(), type, message, sequence, originalSegmentId, blocking);
    }

    public UUID id() {
        return id;
    }

    public NormalizationIssueType type() {
        return type;
    }

    public String message() {
        return message;
    }

    public Optional<Integer> sequence() {
        return Optional.ofNullable(sequence);
    }

    public Optional<UUID> originalSegmentId() {
        return Optional.ofNullable(originalSegmentId);
    }

    public boolean blocking() {
        return blocking;
    }
}
