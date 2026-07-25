package com.nanobaseai.actenora.meetingintelligence.domain.validation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Transcript segment snapshot used by the deterministic rule engine.
 */
public record ValidationSegment(
        UUID segmentId,
        int sequence,
        String speakerId,
        String speakerDisplayName,
        long startOffsetMs,
        long endOffsetMs,
        String content,
        boolean markerNear
) {
    public ValidationSegment {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(content, "content");
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be >= 0");
        }
        if (startOffsetMs < 0 || endOffsetMs < startOffsetMs) {
            throw new IllegalArgumentException("invalid segment timing");
        }
    }

    public Optional<String> speakerIdOptional() {
        return Optional.ofNullable(speakerId);
    }

    public Optional<String> speakerDisplayNameOptional() {
        return Optional.ofNullable(speakerDisplayName);
    }
}
