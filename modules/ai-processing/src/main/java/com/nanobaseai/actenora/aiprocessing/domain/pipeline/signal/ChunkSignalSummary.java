package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

/**
 * Compact prior-chunk summary for continuation-aware gating.
 */
public record ChunkSignalSummary(
        boolean hasRiskSignal,
        boolean hasActionSignal,
        boolean hasMitigationSignal,
        boolean hasDecisionSignal,
        boolean hasOpenQuestionSignal
) {
    public static ChunkSignalSummary empty() {
        return new ChunkSignalSummary(false, false, false, false, false);
    }
}
