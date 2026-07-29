package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import com.nanobaseai.actenora.aiprocessing.application.port.PipelineQualityMetricsPort;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.RiskCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TopicCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinutesEditorialFinalizationTest {

    @Test
    void editorialModeUsesOneBoundedCallAndPreservesValidatedStructure() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<InferenceRequest> captured = new AtomicReference<>();
        ModelRuntimePort runtime = runtime(request -> {
            calls.incrementAndGet();
            captured.set(request);
            return new InferenceResponse(
                    """
                            {
                              "executiveSummary": "Doğrulanan sonuçlar uygulama planına alındı.",
                              "reviewRequired": false
                            }
                            """,
                    321,
                    27,
                    456,
                    "editorial-test@1"
            );
        });
        FinalNoteDraft deterministic = deterministicDraft();
        ExtractionBundle bundle = bundle(deterministic);

        MinutesFinalizationResult result = new MinutesSynthesisAndAudit(
                runtime,
                90,
                PipelineQualityMetricsPort.noop(),
                editorialPolicy(MinutesFinalizationPolicy.FailureMode.DETERMINISTIC)
        ).finalizeMinutes(
                bundle,
                deterministic,
                Set.of("seg-1"),
                "meeting",
                "tr",
                PriorMeetingContext.EMPTY
        );

        assertEquals(1, calls.get());
        assertEquals(1, result.modelCalls());
        assertEquals(321, result.inputTokens());
        assertEquals(27, result.outputTokens());
        assertEquals(456, result.modelLatencyMs());
        assertFalse(result.fallbackUsed());
        assertEquals(768, captured.get().maxOutputTokens());
        assertEquals(90, captured.get().timeoutSeconds());
        assertEquals("meeting.editorial-summary.v1", captured.get().schemaVersion());
        assertFalse(captured.get().userPrompt().contains("Deterministik özet"));
        assertTrue(captured.get().userPrompt().contains("2026-07-30T12:00:00+03:00"));
        assertEquals("Doğrulanan sonuçlar uygulama planına alındı.", result.draft().executiveSummary());
        assertStructuredFieldsEqual(deterministic, result.draft());
    }

    @Test
    void malformedEditorialResponseFallsBackWithoutASecondModelCall() {
        AtomicInteger calls = new AtomicInteger();
        ModelRuntimePort runtime = runtime(request -> {
            calls.incrementAndGet();
            return new InferenceResponse("{}", 111, 3, 222, "editorial-test@1");
        });
        FinalNoteDraft deterministic = deterministicDraft();

        MinutesFinalizationResult result = new MinutesSynthesisAndAudit(
                runtime,
                90,
                PipelineQualityMetricsPort.noop(),
                editorialPolicy(MinutesFinalizationPolicy.FailureMode.DETERMINISTIC)
        ).finalizeMinutes(
                bundle(deterministic),
                deterministic,
                Set.of("seg-1"),
                "meeting",
                "tr",
                PriorMeetingContext.EMPTY
        );

        assertEquals(1, calls.get());
        assertEquals(1, result.modelCalls());
        assertTrue(result.fallbackUsed());
        assertEquals(111, result.inputTokens());
        assertEquals(3, result.outputTokens());
        assertEquals(deterministic.executiveSummary(), result.draft().executiveSummary());
        assertStructuredFieldsEqual(deterministic, result.draft());
    }

    @Test
    void editorialSummaryPreservesStructuredAgendaPrefix() {
        ModelRuntimePort runtime = runtime(request -> new InferenceResponse(
                """
                        {
                          "executiveSummary": "Kararlar ve aksiyonlar doğrulandı.",
                          "reviewRequired": false
                        }
                        """,
                120,
                20,
                100,
                "editorial-test@1"
        ));
        FinalNoteDraft base = deterministicDraft();
        FinalNoteDraft deterministic = new FinalNoteDraft(
                "Gündem:\n1. Oturum yenileme",
                base.decisions(),
                base.actionItems(),
                base.risks(),
                base.openQuestions(),
                base.commitments(),
                List.of(new TopicCandidate("Oturum yenileme", List.of("seg-1"), 0.95)),
                base.issues(),
                base.proposals(),
                base.importantFacts(),
                base.qualityFlags(),
                base.evidenceSegmentIds(),
                base.confidence(),
                base.requiresManualReview()
        );

        MinutesFinalizationResult result = new MinutesSynthesisAndAudit(
                runtime,
                90,
                PipelineQualityMetricsPort.noop(),
                editorialPolicy(MinutesFinalizationPolicy.FailureMode.DETERMINISTIC)
        ).finalizeMinutes(
                bundle(deterministic),
                deterministic,
                Set.of("seg-1"),
                "meeting",
                "tr",
                PriorMeetingContext.EMPTY
        );

        assertEquals(
                "Gündem:\n1. Oturum yenileme\n\nKararlar ve aksiyonlar doğrulandı.",
                result.draft().executiveSummary()
        );
        assertFalse(result.fallbackUsed());
    }

    private static MinutesFinalizationPolicy editorialPolicy(
            MinutesFinalizationPolicy.FailureMode failureMode
    ) {
        return new MinutesFinalizationPolicy(
                MinutesFinalizationPolicy.Mode.EDITORIAL,
                "/aiprocessing/prompts/editorial-summary.v1.txt",
                "pv-meeting-editorial-summary-v1",
                "meeting.editorial-summary.v1",
                "FINAL_NOTE",
                768,
                90,
                failureMode
        );
    }

    private static ModelRuntimePort runtime(
            java.util.function.Function<InferenceRequest, InferenceResponse> inference
    ) {
        return new ModelRuntimePort() {
            @Override
            public ModelDescriptor descriptor() {
                return new ModelDescriptor("test", "editorial-test", "editorial-test@1", 32_768, 2_048);
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

    private static FinalNoteDraft deterministicDraft() {
        DecisionCandidate decision = new DecisionCandidate("Karar metni", List.of("seg-1"), 0.94);
        ActionItemCandidate action = new ActionItemCandidate(
                "Can görevi tamamlayacak",
                "Can",
                "2026-07-30",
                List.of("seg-1"),
                0.93,
                "PERSON",
                "HIGH",
                "yarın",
                "2026-07-30T12:00:00+03:00"
        );
        RiskCandidate risk = new RiskCandidate("Risk metni", List.of("seg-1"), 0.88, "MEDIUM", null);
        return new FinalNoteDraft(
                "Deterministik özet",
                List.of(decision),
                List.of(action),
                List.of(risk),
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

    private static ExtractionBundle bundle(FinalNoteDraft draft) {
        return new ExtractionBundle(
                draft.topics(),
                draft.decisions(),
                draft.actionItems(),
                draft.risks(),
                draft.openQuestions(),
                draft.commitments(),
                draft.issues(),
                draft.proposals(),
                draft.importantFacts(),
                draft.qualityFlags(),
                draft.evidenceSegmentIds(),
                draft.confidence()
        );
    }

    private static void assertStructuredFieldsEqual(FinalNoteDraft expected, FinalNoteDraft actual) {
        assertEquals(expected.decisions(), actual.decisions());
        assertEquals(expected.actionItems(), actual.actionItems());
        assertEquals(expected.risks(), actual.risks());
        assertEquals(expected.openQuestions(), actual.openQuestions());
        assertEquals(expected.commitments(), actual.commitments());
        assertEquals(expected.topics(), actual.topics());
        assertEquals(expected.issues(), actual.issues());
        assertEquals(expected.proposals(), actual.proposals());
        assertEquals(expected.importantFacts(), actual.importantFacts());
        assertEquals(expected.qualityFlags(), actual.qualityFlags());
        assertEquals(expected.evidenceSegmentIds(), actual.evidenceSegmentIds());
        assertEquals(expected.confidence(), actual.confidence());
    }
}
