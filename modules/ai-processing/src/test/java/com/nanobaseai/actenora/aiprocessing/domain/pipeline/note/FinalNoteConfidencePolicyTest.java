package com.nanobaseai.actenora.aiprocessing.domain.pipeline.note;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.MeetingQualityProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalNoteConfidencePolicyTest {

    @Test
    void capsDoubleFallbackAndForcesManualReview() {
        FinalNoteDraft draft = new FinalNoteDraft(
                "summary",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("SYNTHESIS_FALLBACK", "AUDIT_FALLBACK"),
                List.of(),
                0.91d,
                false
        );
        FinalNoteDraft out = new FinalNoteConfidencePolicy(MeetingQualityProperties.defaults()).apply(draft);
        assertEquals(0.45d, out.confidence(), 0.0001);
        assertTrue(out.requiresManualReview());
        assertTrue(out.qualityFlags().contains("REQUIRES_MANUAL_REVIEW"));
    }

    @Test
    void successfulSubsumptionDropDoesNotForceManualReview() {
        FinalNoteDraft draft = new FinalNoteDraft(
                "summary",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        "DECISION_SUBSUMED_PROPOSAL_DROPPED",
                        "CONSISTENCY_AUDIT_PASSED",
                        "auditStatus=PASSED",
                        "unresolvedConflictCount=0",
                        "REQUIRES_MANUAL_REVIEW"
                ),
                List.of(),
                0.92d,
                true
        );
        FinalNoteDraft out = new FinalNoteConfidencePolicy(MeetingQualityProperties.defaults()).apply(draft);
        assertEquals(false, out.requiresManualReview());
        assertTrue(out.qualityFlags().stream().noneMatch(f -> f.equalsIgnoreCase("REQUIRES_MANUAL_REVIEW")));
    }

    @Test
    void unresolvedDecisionProposalConflictForcesManualReview() {
        FinalNoteDraft draft = new FinalNoteDraft(
                "summary",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("UNRESOLVED_DECISION_PROPOSAL_CONFLICT", "CONSISTENCY_AUDIT_NEEDS_REVIEW"),
                List.of(),
                0.9d,
                false
        );
        FinalNoteDraft out = new FinalNoteConfidencePolicy(MeetingQualityProperties.defaults()).apply(draft);
        assertTrue(out.requiresManualReview());
    }
}
