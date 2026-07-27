package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import java.util.List;
import java.util.Objects;

/**
 * Structural / behavioural features for adaptive chunk gating.
 * Dictionaries contribute counts; they never alone decide extract/skip.
 */
public record ChunkSignalFeatures(
        int explicitDecisionMarkers,
        int assignmentStructures,
        int commitmentStructures,
        int deadlineExpressions,
        int moneyExpressions,
        int riskExpressions,
        int mitigationExpressions,
        int openQuestionStructures,
        int proposalExpressions,
        int negatedDecisionExpressions,
        int repetitionCount,
        int uiNoiseCount,
        int continuationSignals,
        double lexicalDiversity,
        double semanticRepetitionRatio,
        int meaningfulSegmentCount
) {
    public ChunkSignalFeatures {
        if (meaningfulSegmentCount < 0) {
            throw new IllegalArgumentException("meaningfulSegmentCount must be >= 0");
        }
    }

    public boolean hasRiskSignal() {
        return riskExpressions > 0;
    }

    public boolean hasActionSignal() {
        return assignmentStructures > 0 || commitmentStructures > 0;
    }

    public boolean hasMitigationSignal() {
        return mitigationExpressions > 0;
    }

    public ChunkSignalSummary toSummary() {
        return new ChunkSignalSummary(
                hasRiskSignal(),
                hasActionSignal(),
                hasMitigationSignal(),
                explicitDecisionMarkers > 0,
                openQuestionStructures > 0
        );
    }

    public static ChunkSignalFeatures empty() {
        return new ChunkSignalFeatures(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0d, 0.0d, 0);
    }
}
