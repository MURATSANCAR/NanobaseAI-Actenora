package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.List;
import java.util.Objects;

/**
 * Pre-flight context window check before calling the model.
 */
public final class ContextWindowGuard {

    private final TokenEstimator tokenEstimator;

    public ContextWindowGuard(TokenEstimator tokenEstimator) {
        this.tokenEstimator = Objects.requireNonNull(tokenEstimator, "tokenEstimator");
    }

    public ContextWindowGuard() {
        this(TokenEstimator.approximate());
    }

    public void assertFits(String promptAndInput, int modelContextWindow, int maxOutputTokens) {
        int inputTokens = tokenEstimator.estimate(promptAndInput);
        int required = inputTokens + maxOutputTokens;
        if (required > modelContextWindow) {
            throw new PipelineException(
                    FailureCategory.CONTEXT_OVERFLOW,
                    PipelineStage.CHUNK,
                    "Context overflow: requiredTokens=" + required
                            + " contextWindow=" + modelContextWindow
            );
        }
    }

    public void assertTranscriptFitsBudget(List<SegmentInput> segments, ChunkingConfig config) {
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(config, "config");
        int usable = config.modelContextWindowTokens()
                - config.promptOverheadTokens()
                - config.maxOutputTokens()
                - config.safetyMarginTokens();
        if (usable <= 0) {
            throw new PipelineException(
                    FailureCategory.CONTEXT_OVERFLOW,
                    PipelineStage.CHUNK,
                    "Context overflow: no usable context after reservations"
            );
        }
        for (SegmentInput segment : segments) {
            int segmentTokens = tokenEstimator.estimate(segment.content());
            if (segmentTokens > usable) {
                throw new PipelineException(
                        FailureCategory.CONTEXT_OVERFLOW,
                        PipelineStage.CHUNK,
                        "Context overflow: segmentTokens=" + segmentTokens + " usable=" + usable
                );
            }
        }
    }
}
