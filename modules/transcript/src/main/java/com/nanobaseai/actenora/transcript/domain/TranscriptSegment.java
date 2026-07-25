package com.nanobaseai.actenora.transcript.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.transcript.api.TranscriptId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Ordered cue/segment within a transcript. Content is stored for processing;
 * application logs must never emit {@link #content()}.
 */
public final class TranscriptSegment {

    private final UUID id;
    private final TenantId tenantId;
    private final TranscriptId transcriptId;
    private final int sequence;
    private final String speakerId;
    private final String speakerDisplayName;
    private final long startOffsetMs;
    private final long endOffsetMs;
    private final String content;
    private final ContentHash contentHash;

    public TranscriptSegment(
            UUID id,
            TenantId tenantId,
            TranscriptId transcriptId,
            int sequence,
            String speakerId,
            String speakerDisplayName,
            long startOffsetMs,
            long endOffsetMs,
            String content) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.transcriptId = Objects.requireNonNull(transcriptId, "transcriptId");
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be >= 0");
        }
        this.sequence = sequence;
        this.speakerId = speakerId;
        this.speakerDisplayName = speakerDisplayName;
        if (startOffsetMs < 0 || endOffsetMs < startOffsetMs) {
            throw new TranscriptDomainException(
                    "INVALID_SEGMENT_TIMING",
                    "Invalid segment timing sequence=" + sequence);
        }
        this.startOffsetMs = startOffsetMs;
        this.endOffsetMs = endOffsetMs;
        this.content = Objects.requireNonNull(content, "content");
        this.contentHash = ContentHash.ofUtf8(content);
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public TranscriptId transcriptId() {
        return transcriptId;
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

    public String content() {
        return content;
    }

    public ContentHash contentHash() {
        return contentHash;
    }
}
