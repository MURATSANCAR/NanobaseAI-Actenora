package com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossTypeConsistencyQualityGateTest {

    @Test
    void finalizationFallbackIsPersistedAndRequiresReview() {
        FinalNoteDraft audited = new CrossTypeConsistencyAuditor().audit(draftWithAction(
                new ActionItemCandidate(
                        "Oturum yenileme yarış koşulunu düzeltecek.",
                        "Selin",
                        null,
                        List.of("seg-1"),
                        0.95
                )
        ), true);

        assertTrue(audited.qualityFlags().contains(CrossTypeConsistencyAuditor.FINALIZATION_FALLBACK));
        assertTrue(audited.qualityFlags().contains("fallbackUsed=true"));
        assertTrue(audited.qualityFlags().contains("auditStatus=NEEDS_REVIEW"));
        assertTrue(audited.requiresManualReview());
    }

    @Test
    void genericFinalActionCannotPassTheQualityGate() {
        FinalNoteDraft audited = new CrossTypeConsistencyAuditor().audit(draftWithAction(
                new ActionItemCandidate(
                        "Düzeltmeyi yapacak.",
                        "Selin",
                        null,
                        List.of("seg-1"),
                        0.95
                )
        ));

        assertTrue(audited.qualityFlags().contains("genericActionCount=1"));
        assertTrue(audited.qualityFlags().contains("auditStatus=NEEDS_REVIEW"));
        assertTrue(audited.requiresManualReview());
        assertFalse(audited.qualityFlags().contains(CrossTypeConsistencyAuditor.AUDIT_PASSED));
    }

    @Test
    void correctedOrPartiallyDroppedReferenceDoesNotMakeSupportedOutputUnsupported() {
        FinalNoteDraft base = draftWithAction(new ActionItemCandidate(
                "Oturum yenileme yarış koşulunu düzeltecek.",
                "Selin",
                null,
                List.of("seg-1"),
                0.95
        ));
        FinalNoteDraft withTelemetry = new FinalNoteDraft(
                base.executiveSummary(), base.decisions(), base.actionItems(), base.risks(),
                base.openQuestions(), base.commitments(), base.topics(), base.issues(),
                base.proposals(), base.importantFacts(), List.of("EVIDENCE_REF_DROPPED"),
                base.evidenceSegmentIds(), base.confidence(), false
        );

        FinalNoteDraft audited = new CrossTypeConsistencyAuditor().audit(withTelemetry);

        assertTrue(audited.qualityFlags().contains("unsupportedItemCount=0"));
        assertTrue(audited.qualityFlags().contains(CrossTypeConsistencyAuditor.AUDIT_PASSED));
        assertFalse(audited.requiresManualReview());
    }

    @Test
    void intentionalSoftDropTelemetryDoesNotForceManualReview() {
        FinalNoteDraft base = draftWithAction(new ActionItemCandidate(
                "Oturum yenileme yarış koşulunu düzeltecek.",
                "Selin",
                null,
                List.of("seg-1"),
                0.95
        ));
        FinalNoteDraft withSoftDrop = new FinalNoteDraft(
                base.executiveSummary(), base.decisions(), base.actionItems(), base.risks(),
                base.openQuestions(), base.commitments(), base.topics(), base.issues(),
                base.proposals(), base.importantFacts(),
                List.of("EVIDENCE_ITEM_SOFT_DROPPED", "EVIDENCE_REF_DROPPED"),
                base.evidenceSegmentIds(), base.confidence(), false
        );

        FinalNoteDraft audited = new CrossTypeConsistencyAuditor().audit(withSoftDrop);

        assertTrue(audited.qualityFlags().contains("unsupportedItemCount=0"));
        assertTrue(audited.qualityFlags().contains(CrossTypeConsistencyAuditor.AUDIT_PASSED));
        assertFalse(audited.requiresManualReview());
    }


    @Test
    void sameTopicDecisionDoesNotDropUnansweredSideOpenQuestion() {
        FinalNoteDraft draft = new FinalNoteDraft(
                "Özet",
                List.of(new DecisionCandidate(
                        "Paralel refresh çağrıları tek promise üzerinde birleştirilecek.",
                        List.of("seg-1"),
                        0.95,
                        null,
                        "DECIDED"
                )),
                List.of(),
                List.of(),
                List.of(
                        new OpenQuestionCandidate(
                                "Yarış koşulunun hangi şartlarda başladığı loglardan ayrıştırılabiliyor mu?",
                                List.of("seg-2"),
                                0.9
                        ),
                        new OpenQuestionCandidate(
                                "Kesinti veya gecikmede ilk kullanıcı iletişimini kim yapacak?",
                                List.of("seg-3"),
                                0.88
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("seg-1", "seg-2", "seg-3"),
                0.9,
                false
        );

        FinalNoteDraft audited = new CrossTypeConsistencyAuditor().audit(draft);

        assertEquals(2, audited.openQuestions().size());
        assertTrue(audited.openQuestions().stream().anyMatch(q -> q.text().contains("loglardan")));
        assertTrue(audited.openQuestions().stream().anyMatch(q -> q.text().contains("iletişimini kim")));
    }


    @Test
    void measuredObservationFactSurvivesActionSubsumption() {
        FinalNoteDraft draft = new FinalNoteDraft(
                "Özet",
                List.of(),
                List.of(new ActionItemCandidate(
                        "Oturum yenileme yarış koşulunu düzeltecek.",
                        "Selin",
                        null,
                        List.of("seg-1"),
                        0.95
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ImportantFactCandidate(
                        "Oturum yenileme testi yapılan 40 tekrarın 3'ünde 401 görüldü.",
                        List.of("seg-2"),
                        0.9
                )),
                List.of(),
                List.of("seg-1", "seg-2"),
                0.9,
                false
        );
        FinalNoteDraft audited = new CrossTypeConsistencyAuditor().audit(draft);
        assertEquals(1, audited.importantFacts().size());
        assertTrue(audited.importantFacts().getFirst().text().contains("401"));
    }

    private static FinalNoteDraft draftWithAction(ActionItemCandidate action) {
        return new FinalNoteDraft(
                "Özet",
                List.of(),
                List.of(action),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("seg-1"),
                0.95,
                false
        );
    }
}
