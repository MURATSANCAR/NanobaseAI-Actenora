package com.nanobaseai.actenora.aiprocessing.application.pipeline;

import com.nanobaseai.actenora.aiprocessing.application.port.PipelineQualityMetricsPort;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.routing.InferenceTaskType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinutesEvidenceAuditTest {

    @Test
    void missingAuditItemFailsClosedToManualReview() {
        MinutesFinalizationResult result = finalizeWithAudit("{\"audits\":[]}");

        assertTrue(result.draft().requiresManualReview());
        assertTrue(result.draft().qualityFlags().contains("AUDIT_COVERAGE_INCOMPLETE"));
        assertTrue(result.draft().qualityFlags().contains("REQUIRES_MANUAL_REVIEW"));
    }

    @Test
    void unsupportedIsRemovedAndPartialIsNeverAutoShareable() {
        MinutesFinalizationResult unsupported = finalizeWithAudit("""
                {"audits":[{
                  "type":"DECISION","text":"Yeni karar","verdict":"UNSUPPORTED","reason":"Kanıt yok"
                }]}
                """);
        assertTrue(unsupported.draft().decisions().isEmpty());
        assertTrue(unsupported.draft().qualityFlags().contains("UNSUPPORTED_ITEMS_DROPPED"));
        assertFalse(unsupported.draft().requiresManualReview());

        MinutesFinalizationResult partial = finalizeWithAudit("""
                {"audits":[{
                  "type":"DECISION","text":"Yeni karar","verdict":"PARTIALLY_SUPPORTED","reason":"Kısmi kanıt"
                }]}
                """);
        assertFalse(partial.draft().decisions().isEmpty());
        assertTrue(partial.draft().requiresManualReview());
        assertTrue(partial.draft().qualityFlags().contains("PARTIAL_EVIDENCE_NEEDS_REVIEW"));
    }

    @Test
    void auditRuntimeFailureFailsClosedToManualReview() {
        ModelRuntimePort runtime = runtime(request -> {
            if (InferenceTaskType.VALIDATION.name().equals(request.taskType())) {
                throw new IllegalStateException("audit unavailable");
            }
            return response(synthesisJson());
        });

        MinutesFinalizationResult result = finalize(runtime);

        assertTrue(result.fallbackUsed());
        assertTrue(result.draft().requiresManualReview());
        assertTrue(result.draft().qualityFlags().contains("AUDIT_FALLBACK"));
        assertTrue(result.draft().qualityFlags().contains("AUDIT_COVERAGE_INCOMPLETE"));
    }

    private static MinutesFinalizationResult finalizeWithAudit(String auditJson) {
        return finalize(runtime(request -> response(
                InferenceTaskType.VALIDATION.name().equals(request.taskType())
                        ? auditJson
                        : synthesisJson()
        )));
    }

    private static MinutesFinalizationResult finalize(ModelRuntimePort runtime) {
        FinalNoteDraft draft = draft();
        return new MinutesSynthesisAndAudit(
                runtime,
                30,
                PipelineQualityMetricsPort.noop(),
                MinutesFinalizationPolicy.compatibility()
        ).finalizeMinutes(
                bundle(draft),
                draft,
                Set.of("seg-1"),
                "meeting",
                "tr",
                PriorMeetingContext.EMPTY
        );
    }

    private static ModelRuntimePort runtime(Function<InferenceRequest, InferenceResponse> inference) {
        return new ModelRuntimePort() {
            @Override
            public ModelDescriptor descriptor() {
                return new ModelDescriptor("test", "audit-test", "audit-test@1", 8_192, 2_048);
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

    private static InferenceResponse response(String json) {
        return new InferenceResponse(json, 10, 10, 1, "audit-test@1");
    }

    private static String synthesisJson() {
        return """
                {
                  "executiveSummary":"Özet",
                  "topics":[],
                  "decisions":[{"text":"Yeni karar","evidenceSegmentIds":["seg-1"],"confidence":0.9}],
                  "actionItems":[],
                  "risks":[],
                  "openQuestions":[],
                  "commitments":[],
                  "issues":[],
                  "proposals":[],
                  "importantFacts":[],
                  "qualityFlags":[],
                  "evidenceSegmentIds":["seg-1"],
                  "confidence":0.9,
                  "reviewRequired":false
                }
                """;
    }

    private static FinalNoteDraft draft() {
        return new FinalNoteDraft(
                "Özet",
                List.of(new DecisionCandidate("Yeni karar", List.of("seg-1"), 0.9)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("seg-1"),
                0.9,
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
}
