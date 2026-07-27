package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TranscriptChunk;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Faz 3 — offline EVAL harness for threshold / classifier tuning against labeled fixtures.
 * Fixtures are synthetic structural cases (no real customer identities).
 */
public final class SignalGateEvalHarness {

    private final ObjectMapper mapper = new ObjectMapper();

    public EvalReport run(String resourcePath, SignalGateConfig config) {
        Objects.requireNonNull(resourcePath, "resourcePath");
        Objects.requireNonNull(config, "config");
        ChunkSignalGate gate = new ChunkSignalGate(config);
        List<EvalCaseResult> results = new ArrayList<>();
        int fn = 0;
        int fp = 0;
        int tp = 0;
        int tn = 0;
        int decisionFn = 0;
        int actionFn = 0;
        int riskFn = 0;
        int mitigationFn = 0;
        int noiseFp = 0;

        for (EvalCase evalCase : loadCases(resourcePath)) {
            ChunkContext ctx = evalCase.previousHasRisk()
                    ? ChunkContext.withPrevious(config, new ChunkSignalSummary(true, false, false, false, false))
                    : ChunkContext.of(config);
            ChunkGateDecision decision = gate.evaluate(evalCase.chunk(), ctx);
            boolean extracted = switch (decision.outcome()) {
                case EXTRACT_STRONG_SIGNAL, EXTRACT_COMPOSITE_SIGNAL, EXTRACT_CONTINUATION, EXTRACT_CLASSIFIER -> true;
                case SKIP_LOW_SIGNAL, SHADOW_SKIP -> false;
            };

            boolean expect = evalCase.expectExtract();
            boolean ok = extracted == expect;
            if (expect && extracted) {
                tp++;
            } else if (!expect && !extracted) {
                tn++;
            } else if (expect) {
                fn++;
                switch (evalCase.kind()) {
                    case "decision" -> decisionFn++;
                    case "action" -> actionFn++;
                    case "risk" -> riskFn++;
                    case "mitigation" -> mitigationFn++;
                    default -> {
                    }
                }
            } else {
                fp++;
                if ("noise".equals(evalCase.kind())) {
                    noiseFp++;
                }
            }
            results.add(new EvalCaseResult(evalCase.id(), expect, extracted, decision.outcome(), decision.score(), ok));
        }

        int signalCases = (int) results.stream().filter(r -> r.expectExtract()).count();
        int noiseCases = results.size() - signalCases;
        double fillerSkipRate = noiseCases == 0 ? 1.0d
                : (double) (noiseCases - noiseFp) / (double) noiseCases;
        double signalRecall = signalCases == 0 ? 1.0d : (double) tp / (double) signalCases;

        return new EvalReport(
                config.policyVersion(),
                config.threshold(),
                results,
                tp, tn, fp, fn,
                decisionFn, actionFn, riskFn, mitigationFn,
                fillerSkipRate,
                signalRecall
        );
    }

    /**
     * Sweeps thresholds and returns the config with best F1 (prefer zero FN on decision/action/risk/mitigation).
     */
    public SignalGateConfig tuneThreshold(String resourcePath, SignalGateConfig base, double min, double max, double step) {
        SignalGateConfig best = base;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (double t = min; t <= max + 1e-9; t += step) {
            SignalGateConfig candidate = new SignalGateConfig(
                    base.enabled(),
                    base.mode(),
                    t,
                    base.continuationAware(),
                    base.semanticRepetitionEnabled(),
                    base.hardMarkerShortcutEnabled(),
                    false,
                    base.classifierEnabled(),
                    base.uncertainBand(),
                    base.policyVersion(),
                    base.dictionaryVersion()
            );
            EvalReport report = run(resourcePath, candidate);
            double precision = (report.tp() + report.fp()) == 0 ? 0.0d
                    : (double) report.tp() / (double) (report.tp() + report.fp());
            double recall = (report.tp() + report.fn()) == 0 ? 0.0d
                    : (double) report.tp() / (double) (report.tp() + report.fn());
            double f1 = (precision + recall) == 0 ? 0.0d : 2 * precision * recall / (precision + recall);
            // Hard penalty for critical FN kinds
            double criticalFn = report.decisionFn() + report.actionFn() + report.riskFn() + report.mitigationFn();
            double score = f1 * 100.0d - criticalFn * 50.0d + report.fillerSkipRate() * 10.0d;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private List<EvalCase> loadCases(String resourcePath) {
        try (InputStream in = SignalGateEvalHarness.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("EVAL resource missing: " + resourcePath);
            }
            JsonNode root = mapper.readTree(in);
            List<EvalCase> cases = new ArrayList<>();
            for (JsonNode n : root.path("cases")) {
                List<SegmentInput> segments = new ArrayList<>();
                int i = 0;
                for (JsonNode seg : n.path("segments")) {
                    segments.add(new SegmentInput("s" + i, i, "Speaker", i * 1000L, i * 1000L + 900L, seg.asText(), false));
                    i++;
                }
                int tokens = Math.max(40, segments.stream().mapToInt(s -> s.content().length() / 4).sum());
                cases.add(new EvalCase(
                        n.path("id").asText(),
                        n.path("expectExtract").asBoolean(),
                        n.path("kind").asText("other"),
                        n.path("previousHasRisk").asBoolean(false),
                        new TranscriptChunk(0, segments, tokens)
                ));
            }
            return cases;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load EVAL cases", ex);
        }
    }

    public record EvalCase(
            String id,
            boolean expectExtract,
            String kind,
            boolean previousHasRisk,
            TranscriptChunk chunk
    ) {
    }

    public record EvalCaseResult(
            String id,
            boolean expectExtract,
            boolean extracted,
            GateOutcome outcome,
            double score,
            boolean ok
    ) {
    }

    public record EvalReport(
            String policyVersion,
            double threshold,
            List<EvalCaseResult> results,
            int tp,
            int tn,
            int fp,
            int fn,
            int decisionFn,
            int actionFn,
            int riskFn,
            int mitigationFn,
            double fillerSkipRate,
            double signalRecall
    ) {
        public boolean meetsPhase3Targets() {
            return decisionFn == 0
                    && actionFn == 0
                    && riskFn == 0
                    && mitigationFn == 0
                    && fillerSkipRate >= 0.80d
                    && signalRecall >= 0.99d;
        }
    }
}
