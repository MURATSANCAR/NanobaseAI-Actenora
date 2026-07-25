package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.Objects;
import java.util.Optional;

/**
 * Normalized transcript segment handed to the pipeline (opaque ids only).
 */
public record SegmentInput(
        String segmentId,
        int sequence,
        String speakerDisplayName,
        long startOffsetMs,
        long endOffsetMs,
        String content,
        boolean markerNear
) {
    public SegmentInput {
        Objects.requireNonNull(segmentId, "segmentId");
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be >= 0");
        }
        Objects.requireNonNull(content, "content");
        if (startOffsetMs < 0 || endOffsetMs < startOffsetMs) {
            throw new IllegalArgumentException("invalid segment timing");
        }
    }

    public Optional<String> speakerDisplayNameOptional() {
        return Optional.ofNullable(speakerDisplayName);
    }

    public SegmentInput withContent(String normalizedContent) {
        return new SegmentInput(
                segmentId,
                sequence,
                speakerDisplayName,
                startOffsetMs,
                endOffsetMs,
                normalizedContent,
                markerNear
        );
    }

    public SegmentInput withMarker(boolean marker) {
        return new SegmentInput(
                segmentId,
                sequence,
                speakerDisplayName,
                startOffsetMs,
                endOffsetMs,
                content,
                marker
        );
    }
}
