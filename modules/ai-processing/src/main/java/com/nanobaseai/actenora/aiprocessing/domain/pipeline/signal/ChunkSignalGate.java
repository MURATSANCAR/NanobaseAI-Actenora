package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TranscriptChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Adaptive two-stage gate: strong structural shortcuts, then composite score.
 * Dictionaries never alone decide skip/extract.
 */
public final class ChunkSignalGate {

    private final ChunkSignalFeatureExtractor featureExtractor;

    public ChunkSignalGate(ChunkSignalFeatureExtractor featureExtractor) {
        this.featureExtractor = Objects.requireNonNull(featureExtractor, "featureExtractor");
    }

    public ChunkSignalGate(SignalGateConfig config) {
        this(new StructuralChunkSignalFeatureExtractor(config));
    }

    public ChunkGateDecision evaluate(TranscriptChunk chunk, ChunkContext context) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(context, "context");
        SignalGateConfig config = context.config();
        ChunkSignalFeatures features = featureExtractor.extract(chunk, context);
        int tokens = Math.max(1, chunk.estimatedTokens());

        if (!config.enabled()) {
            return new ChunkGateDecision(
                    GateOutcome.EXTRACT_COMPOSITE_SIGNAL,
                    0.0d,
                    features,
                    List.of("GATE_DISABLED"),
                    0,
                    config.policyVersion()
            );
        }

        List<String> reasons = new ArrayList<>();

        // Stage 1 — strong structural / continuation
        if (config.continuationAware() && features.continuationSignals() > 0) {
            reasons.add("CONTINUATION_FROM_PREVIOUS_CHUNK");
            return decision(GateOutcome.EXTRACT_CONTINUATION, features, reasons, 0, config);
        }
        if (config.hardMarkerShortcutEnabled()) {
            if (features.explicitDecisionMarkers() > 0
                    && (features.deadlineExpressions() > 0
                    || features.assignmentStructures() > 0
                    || features.moneyExpressions() > 0
                    || chunk.joinedContent().length() > 40)) {
                reasons.add("STRONG_DECISION_STRUCTURE");
                return decision(GateOutcome.EXTRACT_STRONG_SIGNAL, features, reasons, 0, config);
            }
            if (features.assignmentStructures() > 0 && features.deadlineExpressions() > 0) {
                reasons.add("OWNER_TASK_DEADLINE");
                return decision(GateOutcome.EXTRACT_STRONG_SIGNAL, features, reasons, 0, config);
            }
            if (features.commitmentStructures() > 0
                    && (features.deadlineExpressions() > 0 || features.assignmentStructures() > 0)) {
                reasons.add("COMMITMENT_WITH_DELIVERABLE");
                return decision(GateOutcome.EXTRACT_STRONG_SIGNAL, features, reasons, 0, config);
            }
            if (features.riskExpressions() > 0
                    && (features.moneyExpressions() > 0 || features.mitigationExpressions() > 0)) {
                reasons.add("RISK_WITH_IMPACT_OR_MITIGATION");
                return decision(GateOutcome.EXTRACT_STRONG_SIGNAL, features, reasons, 0, config);
            }
            if (features.openQuestionStructures() > 0 && features.meaningfulSegmentCount() > 0) {
                reasons.add("OPEN_QUESTION_WITH_CONTEXT");
                return decision(GateOutcome.EXTRACT_STRONG_SIGNAL, features, reasons, 0, config);
            }
            if (features.mitigationExpressions() > 0 && features.commitmentStructures() > 0) {
                reasons.add("MITIGATION_COMMITMENT");
                return decision(GateOutcome.EXTRACT_STRONG_SIGNAL, features, reasons, 0, config);
            }
        }

        // Stage 2 — composite score (density is supportive via meaningfulSegmentCount)
        double positive =
                features.explicitDecisionMarkers() * 5.0d
                        + features.assignmentStructures() * 4.0d
                        + features.commitmentStructures() * 3.0d
                        + features.deadlineExpressions() * 2.0d
                        + features.riskExpressions() * 2.0d
                        + features.mitigationExpressions() * 2.0d
                        + features.moneyExpressions() * 2.0d
                        + features.openQuestionStructures() * 1.0d
                        + features.continuationSignals() * 2.0d;

        double noise =
                features.uiNoiseCount() * 3.0d
                        + features.semanticRepetitionRatio() * 4.0d
                        + features.proposalExpressions() * 1.0d
                        + features.negatedDecisionExpressions() * 1.0d
                        + features.repetitionCount() * 1.0d;

        double finalScore = positive - noise;
        double normalized = finalScore / Math.max(1, features.meaningfulSegmentCount());

        if (features.uiNoiseCount() > 0
                && features.explicitDecisionMarkers() == 0
                && features.assignmentStructures() == 0
                && features.commitmentStructures() == 0
                && features.riskExpressions() == 0
                && features.mitigationExpressions() == 0
                && features.openQuestionStructures() == 0) {
            reasons.add("MEETING_UI_NOISE_DOMINANT");
        }
        if (features.assignmentStructures() == 0 && features.commitmentStructures() == 0) {
            reasons.add("NO_OWNER_TASK_STRUCTURE");
        }
        if (features.deadlineExpressions() == 0 && features.moneyExpressions() == 0) {
            reasons.add("NO_DEADLINE_OR_DELIVERABLE");
        }
        if (features.semanticRepetitionRatio() >= 0.5d) {
            reasons.add("HIGH_SEMANTIC_REPETITION");
        }

        if (normalized >= config.threshold() || finalScore >= config.threshold()) {
            reasons.add("COMPOSITE_SCORE_ABOVE_THRESHOLD");
            return decision(GateOutcome.EXTRACT_COMPOSITE_SIGNAL, features, reasons, 0, config, normalized);
        }

        reasons.add("LOW_COMPOSITE_SIGNAL");
        return decision(GateOutcome.SKIP_LOW_SIGNAL, features, reasons, tokens, config, normalized);
    }

    private static ChunkGateDecision decision(
            GateOutcome outcome,
            ChunkSignalFeatures features,
            List<String> reasons,
            int tokensSaved,
            SignalGateConfig config
    ) {
        return decision(outcome, features, reasons, tokensSaved, config, 0.0d);
    }

    private static ChunkGateDecision decision(
            GateOutcome outcome,
            ChunkSignalFeatures features,
            List<String> reasons,
            int tokensSaved,
            SignalGateConfig config,
            double score
    ) {
        return new ChunkGateDecision(
                outcome,
                score,
                features,
                reasons,
                tokensSaved,
                config.policyVersion()
        );
    }
}
