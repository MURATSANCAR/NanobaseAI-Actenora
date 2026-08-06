package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import com.nanobaseai.actenora.aiprocessing.application.port.PipelineQualityMetricsPort;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.RiskCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.note.FinalizationProvenance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared-fixture regression across DETERMINISTIC / EDITORIAL / FULL finalization modes.
 */
class MinutesFinalizationModeRegressionTest {

    @Test
    void deterministicPreservesStructureAndModeMetadata() {
        MinutesFinalizationResult result = finalizeWith(
                MinutesFinalizationPolicy.Mode.DETERMINISTIC,
                unusedRuntime()
        );
        assertEquals(MinutesFinalizationPolicy.Mode.DETERMINISTIC.name(), result.requestedMode());
        assertEquals(MinutesFinalizationPolicy.Mode.DETERMINISTIC.name(), result.effectiveMode());
        assertFalse(result.fallbackUsed());
        assertEquals("Deterministik özet", result.draft().executiveSummary());
        assertEquals(1, result.draft().decisions().size());
        assertEquals(1, result.draft().actionItems().size());
        assertTrue(result.draft().qualityFlags().stream()
                .anyMatch(f -> f.startsWith("requestedMode=")));
    }

    @Test
    void editorialRewritesSummaryOnlyAndKeepsValidatedItems() {
        AtomicInteger calls = new AtomicInteger();
        MinutesFinalizationResult result = finalizeWith(
                MinutesFinalizationPolicy.Mode.EDITORIAL,
                runtime(request -> {
                    calls.incrementAndGet();
                    return new InferenceResponse(
                            """
                                    {"executiveSummary":"Editöryel özet.","reviewRequired":false}
                                    """,
                            10, 5, 20, "editorial@1");
                })
        );
        assertEquals(1, calls.get());
        assertEquals(MinutesFinalizationPolicy.Mode.EDITORIAL.name(), result.effectiveMode());
        assertEquals("Editöryel özet.", result.draft().executiveSummary());
        assertEquals("Karar metni", result.draft().decisions().getFirst().text());
        assertEquals("Can", result.draft().actionItems().getFirst().owner());
        assertFalse(result.fallbackUsed());
    }

    @Test
    void fullFallsBackDeterministicallyWhenSynthesisJsonIsInvalid() {
        MinutesFinalizationResult result = finalizeWith(
                MinutesFinalizationPolicy.Mode.FULL,
                runtime(request -> new InferenceResponse("not-json", 1, 1, 1, "full@1"))
        );
        assertEquals(MinutesFinalizationPolicy.Mode.FULL.name(), result.requestedMode());
        // Invalid synthesis → stage fallback; draft still has structured items from deterministic path.
        assertEquals(1, result.draft().decisions().size());
        assertEquals(1, result.draft().actionItems().size());
    }

    @Test
    void finalizationProvenanceMapContainsModes() {
        FinalizationProvenance p = FinalizationProvenance.from(
                "COMPOSER", "MANUAL_REVIEW", "COMPOSER_HIGH_EVIDENCE_REJECTION",
                true, 1, 12L
        );
        Map<String, Object> map = p.toMap();
        assertEquals(FinalizationProvenance.ARTIFACT_TYPE, map.get("artifactType"));
        assertEquals("COMPOSER", map.get("requestedMode"));
        assertEquals("MANUAL_REVIEW", map.get("effectiveMode"));
        assertEquals("COMPOSER_HIGH_EVIDENCE_REJECTION", map.get("fallbackReason"));
        assertEquals(true, map.get("fallbackUsed"));
    }

    private static MinutesFinalizationResult finalizeWith(
            MinutesFinalizationPolicy.Mode mode,
            ModelRuntimePort runtime
    ) {
        FinalNoteDraft deterministic = fixtureDraft();
        ExtractionBundle bundle = new ExtractionBundle(
                deterministic.topics(),
                deterministic.decisions(),
                deterministic.actionItems(),
                deterministic.risks(),
                deterministic.openQuestions(),
                deterministic.commitments(),
                deterministic.issues(),
                deterministic.proposals(),
                deterministic.importantFacts(),
                deterministic.qualityFlags(),
                deterministic.evidenceSegmentIds(),
                deterministic.confidence()
        );
        MinutesFinalizationPolicy policy = switch (mode) {
            case DETERMINISTIC -> new MinutesFinalizationPolicy(
                    MinutesFinalizationPolicy.Mode.DETERMINISTIC,
                    null, null, null, null, 0, 30,
                    MinutesFinalizationPolicy.FailureMode.DETERMINISTIC
            );
            case EDITORIAL -> new MinutesFinalizationPolicy(
                    MinutesFinalizationPolicy.Mode.EDITORIAL,
                    "/aiprocessing/prompts/editorial-summary.v1.txt",
                    "pv-meeting-editorial-summary-v1",
                    "meeting.editorial-summary.v1",
                    "FINAL_NOTE",
                    768,
                    90,
                    MinutesFinalizationPolicy.FailureMode.DETERMINISTIC
            );
            case FULL -> new MinutesFinalizationPolicy(
                    MinutesFinalizationPolicy.Mode.FULL,
                    "/aiprocessing/prompts/final-minutes.v1.txt",
                    "pv-final",
                    "meeting.final-minutes.v1",
                    "FINAL_NOTE",
                    2048,
                    90,
                    MinutesFinalizationPolicy.FailureMode.DETERMINISTIC
            );
            case COMPOSER -> throw new IllegalArgumentException("COMPOSER covered by characterization suite");
            case GROUNDED -> throw new IllegalArgumentException("GROUNDED covered by grounded finalization tests");
        };
        return new MinutesSynthesisAndAudit(
                runtime, 90, PipelineQualityMetricsPort.noop(), policy
        ).finalizeMinutes(bundle, deterministic, Set.of("seg-1"), "meeting", "tr", PriorMeetingContext.EMPTY);
    }

    private static FinalNoteDraft fixtureDraft() {
        return new FinalNoteDraft(
                "Deterministik özet",
                List.of(new DecisionCandidate("Karar metni", List.of("seg-1"), 0.94)),
                List.of(new ActionItemCandidate(
                        "Can görevi tamamlayacak", "Can", "2026-07-30",
                        List.of("seg-1"), 0.93)),
                List.of(new RiskCandidate("Risk metni", List.of("seg-1"), 0.88)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("SOURCE_QUALITY_FLAG"),
                List.of("seg-1"),
                0.91,
                false
        );
    }

    private static ModelRuntimePort unusedRuntime() {
        return runtime(request -> {
            throw new UnsupportedOperationException("deterministic mode must not call the model");
        });
    }

    private static ModelRuntimePort runtime(
            java.util.function.Function<InferenceRequest, InferenceResponse> inference
    ) {
        return new ModelRuntimePort() {
            @Override
            public ModelDescriptor descriptor() {
                return new ModelDescriptor("test", "mode-regression", "mode-regression@1", 32_768, 2_048);
            }

            @Override
            public InferenceResponse infer(InferenceRequest request) {
                return inference.apply(request);
            }

            @Override
            public boolean healthy() {
                return true;
            }
        };
    }
}
