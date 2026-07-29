package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;

import java.util.Objects;

/**
 * Final minutes output together with per-stage model telemetry.
 */
public record MinutesFinalizationResult(
        FinalNoteDraft draft,
        String mode,
        int modelCalls,
        long inputTokens,
        long outputTokens,
        long modelLatencyMs,
        boolean fallbackUsed
) {
    public MinutesFinalizationResult {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(mode, "mode");
        if (modelCalls < 0 || inputTokens < 0 || outputTokens < 0 || modelLatencyMs < 0) {
            throw new IllegalArgumentException("Finalization telemetry values must be >= 0");
        }
    }
}
