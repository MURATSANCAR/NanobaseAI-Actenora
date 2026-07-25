package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.List;
import java.util.Objects;

/**
 * Contiguous whole-segment window for extraction.
 */
public record TranscriptChunk(
        int index,
        List<SegmentInput> segments,
        int estimatedTokens
) {
    public TranscriptChunk {
        Objects.requireNonNull(segments, "segments");
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("chunk must contain at least one segment");
        }
        segments = List.copyOf(segments);
        if (estimatedTokens < 0) {
            throw new IllegalArgumentException("estimatedTokens must be >= 0");
        }
    }

    public List<String> segmentIds() {
        return segments.stream().map(SegmentInput::segmentId).toList();
    }

    public String joinedContent() {
        StringBuilder sb = new StringBuilder();
        for (SegmentInput segment : segments) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            segment.speakerDisplayNameOptional().ifPresent(name -> sb.append(name).append(": "));
            sb.append(segment.content());
        }
        return sb.toString();
    }
}
