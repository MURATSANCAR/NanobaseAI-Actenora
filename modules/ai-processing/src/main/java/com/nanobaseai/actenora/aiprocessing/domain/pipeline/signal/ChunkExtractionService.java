package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TranscriptChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Single entry for sync + staged EXTRACT: gate → (optional) infer → grounding.
 */
public final class ChunkExtractionService {

    private final ChunkSignalGate gate;
    private final BundleGroundingPolicy groundingPolicy;
    private final SignalGateConfig config;
    private final ChunkGateMetrics metrics;

    public ChunkExtractionService(
            ChunkSignalGate gate,
            BundleGroundingPolicy groundingPolicy,
            SignalGateConfig config,
            ChunkGateMetrics metrics
    ) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.groundingPolicy = Objects.requireNonNull(groundingPolicy, "groundingPolicy");
        this.config = Objects.requireNonNull(config, "config");
        this.metrics = metrics == null ? ChunkGateMetrics.NOOP : metrics;
    }

    public static ChunkExtractionService createDefault() {
        SignalGateConfig cfg = SignalGateConfigLoader.load();
        return create(cfg, ChunkGateMetricListener.NOOP);
    }

    public static ChunkExtractionService create(SignalGateConfig config) {
        return create(config, ChunkGateMetricListener.NOOP);
    }

    public static ChunkExtractionService create(SignalGateConfig config, ChunkGateMetricListener listener) {
        ChunkGateMetrics metrics = listener == null || listener == ChunkGateMetricListener.NOOP
                ? new ChunkGateMetrics()
                : new ChunkGateMetrics(listener);
        metrics.setPolicyVersion(config.policyVersion());
        return new ChunkExtractionService(
                new ChunkSignalGate(config),
                new EvidenceBundleGroundingPolicy(),
                config,
                metrics
        );
    }

    public ChunkExtractionResult extract(
            TranscriptChunk chunk,
            ChunkContext context,
            Function<TranscriptChunk, ExtractionBundle> inferencer
    ) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(inferencer, "inferencer");

        metrics.setPolicyVersion(config.policyVersion());
        metrics.incrementTotal();
        ChunkGateDecision decision = gate.evaluate(chunk, context);
        EvidenceIndex evidence = EvidenceIndex.from(chunk);

        if (decision.shouldHardSkip()) {
            if (config.shadowMode()) {
                ExtractionBundle raw = inferencer.apply(chunk);
                ExtractionBundle grounded = groundingPolicy.retainGroundedItems(raw, evidence);
                if (hasExtractableSignal(grounded)) {
                    metrics.incrementShadowFalseNegative();
                } else {
                    metrics.incrementSkipped(chunk.estimatedTokens());
                }
                ChunkGateDecision shadow = new ChunkGateDecision(
                        GateOutcome.SHADOW_SKIP,
                        decision.score(),
                        decision.features(),
                        withReason(decision.reasons(), "SHADOW_MODE_INFERRED"),
                        chunk.estimatedTokens(),
                        decision.policyVersion()
                );
                return ChunkExtractionResult.extracted(grounded, shadow);
            }
            metrics.incrementSkipped(chunk.estimatedTokens());
            return ChunkExtractionResult.skipped(decision, emptySkippedBundle(decision));
        }

        if (decision.outcome() == GateOutcome.EXTRACT_CONTINUATION) {
            metrics.incrementContinuation();
        } else if (decision.outcome() == GateOutcome.EXTRACT_CLASSIFIER) {
            metrics.incrementClassifierExtract();
            metrics.incrementExtracted();
        } else {
            metrics.incrementExtracted();
        }

        ExtractionBundle raw = inferencer.apply(chunk);
        ExtractionBundle grounded = groundingPolicy.retainGroundedItems(raw, evidence);
        if (grounded.qualityFlags().contains("UNSUPPORTED_DECISION")
                || grounded.qualityFlags().contains("DECISION_SOFT_DROPPED")) {
            metrics.incrementDecisionUnsupported();
        }
        return ChunkExtractionResult.extracted(grounded, decision);
    }

    public SignalGateConfig config() {
        return config;
    }

    public ChunkGateMetrics metrics() {
        return metrics;
    }

    private static boolean hasExtractableSignal(ExtractionBundle bundle) {
        return !bundle.decisions().isEmpty()
                || !bundle.actionItems().isEmpty()
                || !bundle.risks().isEmpty()
                || !bundle.commitments().isEmpty()
                || !bundle.openQuestions().isEmpty();
    }

    private static ExtractionBundle emptySkippedBundle(ChunkGateDecision decision) {
        List<String> flags = new ArrayList<>();
        flags.add("SKIPPED_LOW_SIGNAL");
        flags.add("GATE_" + decision.outcome().name());
        flags.add("POLICY_" + decision.policyVersion());
        return new ExtractionBundle(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                flags,
                List.of(),
                0.0d
        );
    }

    private static List<String> withReason(List<String> reasons, String extra) {
        List<String> out = new ArrayList<>(reasons);
        if (!out.contains(extra)) {
            out.add(extra);
        }
        return out;
    }
}
