package com.nanobaseai.actenora.transcript.api.dto;

import com.nanobaseai.actenora.transcript.domain.TranscriptStatus;

import java.util.UUID;

public record TranscriptUploadResponse(
        UUID transcriptId,
        String contentHash,
        TranscriptStatus status,
        String rawStorageKey,
        boolean duplicate
) {
}
