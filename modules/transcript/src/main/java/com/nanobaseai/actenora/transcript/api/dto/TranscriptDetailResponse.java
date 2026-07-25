package com.nanobaseai.actenora.transcript.api.dto;

import com.nanobaseai.actenora.transcript.domain.Transcript;
import com.nanobaseai.actenora.transcript.domain.TranscriptStatus;

import java.time.Instant;
import java.util.UUID;

/** Metadata-only transcript view — never includes segment/raw content. */
public record TranscriptDetailResponse(
        UUID transcriptId,
        UUID meetingOccurrenceId,
        TranscriptStatus status,
        String contentHash,
        String rawStorageKey,
        String language,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static TranscriptDetailResponse from(Transcript transcript) {
        return new TranscriptDetailResponse(
                transcript.id().value(),
                transcript.meetingOccurrenceId(),
                transcript.status(),
                transcript.contentHash().sha256Hex(),
                transcript.rawStorageKey(),
                transcript.language().orElse(null),
                transcript.createdAt(),
                transcript.updatedAt(),
                transcript.version()
        );
    }
}
