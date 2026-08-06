package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.RiskCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TopicCandidate;
import com.nanobaseai.actenora.meetingintelligence.api.dto.AiCandidateBundle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalNoteDraftMapperTest {

    @Test
    void mapsDraftFieldsAndManualReviewFlag() {
        FinalNoteDraft draft = new FinalNoteDraft(
                "Exec summary",
                List.of(new DecisionCandidate("Ship Friday", List.of("seg-1"), 0.9)),
                List.of(new ActionItemCandidate("Prepare release", "Alice", "2026-08-01", List.of("seg-1"), 0.8)),
                List.of(new RiskCandidate("Timeline slip", List.of("seg-1"), 0.7)),
                List.of(new OpenQuestionCandidate("Who owns QA?", List.of("seg-1"), 0.6)),
                List.of(new CommitmentCandidate("Alice owns QA", "Alice", List.of("seg-1"), 0.85)),
                List.of(new TopicCandidate("Delivery and release planning", List.of("seg-1"), 0.9)),
                List.of(),
                List.of(),
                List.of(),
                List.of("LOW_CONFIDENCE"),
                List.of("seg-1"),
                0.88,
                true
        );

        AiCandidateBundle bundle = FinalNoteDraftMapper.toBundle(draft);

        assertEquals("Exec summary", bundle.executiveSummary());
        assertEquals(1, bundle.decisions().size());
        assertEquals("Ship Friday", bundle.decisions().getFirst().text());
        assertEquals("Alice", bundle.actionItems().getFirst().owner());
        assertEquals("2026-08-01", bundle.actionItems().getFirst().dueDate());
        assertEquals(1, bundle.risks().size());
        assertEquals(1, bundle.openQuestions().size());
        assertEquals(1, bundle.commitments().size());
        assertTrue(bundle.issues().isEmpty());
        assertTrue(bundle.proposals().isEmpty());
        assertEquals(1, bundle.importantFacts().size());
        assertEquals("Delivery and release planning", bundle.importantFacts().getFirst().text());
        assertTrue(bundle.qualityFlags().contains("LOW_CONFIDENCE"));
        assertTrue(bundle.qualityFlags().contains("REQUIRES_MANUAL_REVIEW"));
        assertEquals(0.88, bundle.confidence());
    }
}
