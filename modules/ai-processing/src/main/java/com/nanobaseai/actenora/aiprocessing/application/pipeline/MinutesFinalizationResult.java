package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;

import java.util.Objects;

/**
 * Final minutes output together with per-stage model telemetry and explicit mode fallback audit.
 */
public record MinutesFinalizationResult(
        FinalNoteDraft draft,
        String mode,
        int modelCalls,
        long inputTokens,
        long outputTokens,
        long modelLatencyMs,
        boolean fallbackUsed,
        String requestedMode,
        String effectiveMode,
        String fallbackReason
) {
    public MinutesFinalizationResult(
            FinalNoteDraft draft,
            String mode,
            int modelCalls,
            long inputTokens,
            long outputTokens,
            long modelLatencyMs,
            boolean fallbackUsed
    ) {
        this(draft, mode, modelCalls, inputTokens, outputTokens, modelLatencyMs, fallbackUsed,
                mode, mode, null);
    }

    public MinutesFinalizationResult {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(requestedMode, "requestedMode");
        Objects.requireNonNull(effectiveMode, "effectiveMode");
        if (modelCalls < 0 || inputTokens < 0 || outputTokens < 0 || modelLatencyMs < 0) {
            throw new IllegalArgumentException("Finalization telemetry values must be >= 0");
        }
    }

    public static MinutesFinalizationResult of(
            FinalNoteDraft draft,
            String requestedMode,
            String effectiveMode,
            String fallbackReason,
            int modelCalls,
            long inputTokens,
            long outputTokens,
            long modelLatencyMs
    ) {
        boolean fallback = (fallbackReason != null && !fallbackReason.isBlank())
                || !Objects.equals(requestedMode, effectiveMode);
        return new MinutesFinalizationResult(
                draft,
                effectiveMode,
                modelCalls,
                inputTokens,
                outputTokens,
                modelLatencyMs,
                fallback,
                requestedMode,
                effectiveMode,
                fallbackReason
        );
    }
}
