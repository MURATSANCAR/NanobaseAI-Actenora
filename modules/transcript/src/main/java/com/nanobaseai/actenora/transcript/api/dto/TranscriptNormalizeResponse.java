package com.nanobaseai.actenora.transcript.api.dto;

import com.nanobaseai.actenora.transcript.application.TranscriptNormalizationService;
import com.nanobaseai.actenora.transcript.domain.TranscriptStatus;
import com.nanobaseai.actenora.transcript.domain.normalization.NormalizationRunStatus;

import java.util.UUID;

public record TranscriptNormalizeResponse(
        UUID transcriptId,
        UUID runId,
        String normalizationVersion,
        NormalizationRunStatus runStatus,
        TranscriptStatus transcriptStatus,
        String normalizedHash,
        String normalizedStorageKey,
        boolean idempotentHit
) {
    public static TranscriptNormalizeResponse from(TranscriptNormalizationService.NormalizeResult result) {
        return new TranscriptNormalizeResponse(
                result.transcript().id().value(),
                result.run().id(),
                result.run().normalizationVersion(),
                result.run().status(),
                result.transcript().status(),
                result.run().normalizedTranscriptHash().map(h -> h.sha256Hex()).orElse(null),
                result.transcript().normalizedStorageKey().orElse(null),
                result.idempotentHit());
    }
}
