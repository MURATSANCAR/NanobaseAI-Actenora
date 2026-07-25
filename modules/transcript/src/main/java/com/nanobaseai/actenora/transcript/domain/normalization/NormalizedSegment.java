package com.nanobaseai.actenora.transcript.domain.normalization;

import com.nanobaseai.actenora.transcript.domain.ContentHash;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One normalized cue retaining the link back to its original segment.
 */
public final class NormalizedSegment {

    private final UUID id;
    private final UUID originalSegmentId;
    private final int sequence;
    private final String speakerId;
    private final String speakerDisplayName;
    private final long startOffsetMs;
    private final long endOffsetMs;
    private final String originalContent;
    private final String normalizedContent;
    private final ContentHash normalizedContentHash;
    private final SpeakerResolution speakerResolution;

    public NormalizedSegment(
            UUID id,
            UUID originalSegmentId,
            int sequence,
            String speakerId,
            String speakerDisplayName,
            long startOffsetMs,
            long endOffsetMs,
            String originalContent,
            String normalizedContent,
            SpeakerResolution speakerResolution) {
        this.id = Objects.requireNonNull(id, "id");
        this.originalSegmentId = Objects.requireNonNull(originalSegmentId, "originalSegmentId");
        this.sequence = sequence;
        this.speakerId = speakerId;
        this.speakerDisplayName = speakerDisplayName;
        this.startOffsetMs = startOffsetMs;
        this.endOffsetMs = endOffsetMs;
        this.originalContent = Objects.requireNonNull(originalContent, "originalContent");
        this.normalizedContent = Objects.requireNonNull(normalizedContent, "normalizedContent");
        this.normalizedContentHash = ContentHash.ofUtf8(normalizedContent);
        this.speakerResolution = Objects.requireNonNull(speakerResolution, "speakerResolution");
    }

    public UUID id() {
        return id;
    }

    public UUID originalSegmentId() {
        return originalSegmentId;
    }

    public int sequence() {
        return sequence;
    }

    public Optional<String> speakerId() {
        return Optional.ofNullable(speakerId);
    }

    public Optional<String> speakerDisplayName() {
        return Optional.ofNullable(speakerDisplayName);
    }

    public long startOffsetMs() {
        return startOffsetMs;
    }

    public long endOffsetMs() {
        return endOffsetMs;
    }

    public String originalContent() {
        return originalContent;
    }

    public String normalizedContent() {
        return normalizedContent;
    }

    public ContentHash normalizedContentHash() {
        return normalizedContentHash;
    }

    public SpeakerResolution speakerResolution() {
        return speakerResolution;
    }

    /**
     * Canonical line used for transcript-level hash (deterministic, locale-stable).
     */
    public String canonicalLine() {
        return sequence
                + "|"
                + startOffsetMs
                + "|"
                + endOffsetMs
                + "|"
                + speakerDisplayName().orElse("")
                + "|"
                + normalizedContent;
    }
}
