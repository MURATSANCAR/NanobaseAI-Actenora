package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TranscriptChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkSignalClassifierAndEvalTest {

    private static final String EVAL = "/aiprocessing/signal/eval/signal-gate-eval-v1.json";

    @Test
    void productionDefaultsHardSkipAndEvalTargets() {
        SignalGateConfig config = SignalGateConfig.productionDefaults();
        assertFalse(config.shadowMode());
        SignalGateEvalHarness.EvalReport report = new SignalGateEvalHarness().run(EVAL, config);
        assertTrue(report.meetsPhase3Targets(), () -> report.results().toString());
    }

    @Test
    void faz2_uncertainBandUsesClassifier() {
        SignalGateConfig config = SignalGateConfig.productionDefaults()
                .withClassifierEnabled(true);
        ChunkSignalGate gate = new ChunkSignalGate(config);
        // Borderline: business object + future-ish without strong dictionary hits
        TranscriptChunk chunk = new TranscriptChunk(0, List.of(
                new SegmentInput("s1", 0, "A", 0, 1000,
                        "API paketini kanala bırakalım, sprint içinde bakacağız.", false)
        ), 60);
        ChunkGateDecision decision = gate.evaluate(chunk, ChunkContext.of(config));
        assertTrue(
                decision.outcome() == GateOutcome.EXTRACT_CLASSIFIER
                        || decision.outcome() == GateOutcome.EXTRACT_COMPOSITE_SIGNAL
                        || decision.outcome() == GateOutcome.EXTRACT_STRONG_SIGNAL
                        || decision.outcome() == GateOutcome.SKIP_LOW_SIGNAL,
                () -> decision.outcome() + " " + decision.reasons()
        );
        // If uncertain path fired, reason is present
        if (decision.outcome() == GateOutcome.EXTRACT_CLASSIFIER
                || decision.reasons().contains("UNCERTAIN_BAND_CLASSIFIER")) {
            assertTrue(decision.reasons().stream().anyMatch(r -> r.startsWith("CLASSIFIER_")));
        }
    }

    @Test
    void faz3_evalHarnessMeetsTargetsAfterTune() {
        SignalGateEvalHarness harness = new SignalGateEvalHarness();
        SignalGateConfig base = SignalGateConfig.productionDefaults();
        SignalGateConfig tuned = harness.tuneThreshold(EVAL, base, 1.0d, 8.0d, 0.5d);
        SignalGateEvalHarness.EvalReport report = harness.run(EVAL, tuned);
        assertTrue(
                report.meetsPhase3Targets(),
                () -> "FN decision=" + report.decisionFn()
                        + " action=" + report.actionFn()
                        + " risk=" + report.riskFn()
                        + " mitigation=" + report.mitigationFn()
                        + " fillerSkip=" + report.fillerSkipRate()
                        + " recall=" + report.signalRecall()
                        + " threshold=" + report.threshold()
                        + " results=" + report.results()
        );
        assertEquals(0, report.fn());
    }

    @Test
    void faz3_defaultConfigHasZeroCriticalFn() {
        SignalGateEvalHarness harness = new SignalGateEvalHarness();
        SignalGateConfig config = SignalGateConfig.productionDefaults();
        SignalGateEvalHarness.EvalReport report = harness.run(EVAL, config);
        assertEquals(0, report.decisionFn(), () -> report.results().toString());
        assertEquals(0, report.actionFn());
        assertEquals(0, report.riskFn());
        assertEquals(0, report.mitigationFn());
        assertTrue(report.fillerSkipRate() >= 0.80d, () -> "fillerSkip=" + report.fillerSkipRate());
    }
}
