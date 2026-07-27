package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import java.util.List;
import java.util.Objects;

/**
 * Explainable gate result — never a bare boolean.
 */
public record ChunkGateDecision(
        GateOutcome outcome,
        double score,
        ChunkSignalFeatures features,
        List<String> reasons,
        int estimatedTokensSaved,
        String policyVersion
) {
    public ChunkGateDecision {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(features, "features");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        Objects.requireNonNull(policyVersion, "policyVersion");
    }

    public boolean shouldHardSkip() {
        return outcome == GateOutcome.SKIP_LOW_SIGNAL;
    }

    public boolean shouldExtract() {
        return outcome == GateOutcome.EXTRACT_STRONG_SIGNAL
                || outcome == GateOutcome.EXTRACT_COMPOSITE_SIGNAL
                || outcome == GateOutcome.EXTRACT_CONTINUATION
                || outcome == GateOutcome.SHADOW_SKIP;
    }
}
