package com.nanobaseai.actenora.aiprocessing.domain.pipeline.filter;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TopicCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossTypeMeetingItemScrubberTest {

    private final CrossTypeMeetingItemScrubber scrubber = CrossTypeMeetingItemScrubber.productionDefaults();

    @Test
    void dropsStatusQuoFactKeepsExplicitDecisionAndAction() {
        ExtractionBundle input = new ExtractionBundle(
                List.of(new TopicCandidate("Bu noktayı açmamız iyi olur", List.of("t1"), 0.8)),
                List.of(
                        new DecisionCandidate("Kurul, mevcut kararı değiştirmemeye karar verdi.", List.of("d1"), 0.95),
                        new DecisionCandidate("Mevcut kararı değiştirmiyoruz.", List.of("d2"), 0.9)
                ),
                List.of(new ActionItemCandidate(
                        "Mevcut sözleşme listesini güncelle.", "Ada", null, List.of("a1"), 0.9)),
                List.of(),
                List.of(new OpenQuestionCandidate("Bu noktayı biraz açmamız iyi olur.", List.of("q1"), 0.8)),
                List.of(new CommitmentCandidate(
                        "Alınan kararları tutanağa taşıyoruz.", null, List.of("c1"), 0.9)),
                List.of(),
                List.of(),
                List.of(new ImportantFactCandidate("Mevcut kararı değiştirmiyoruz.", List.of("f1"), 0.9)),
                List.of(),
                List.of("d1", "d2", "a1", "q1", "c1", "f1", "t1"),
                0.9
        );

        ExtractionBundle out = scrubber.scrub(input);
        assertEquals(1, out.decisions().size());
        assertEquals("Kurul, mevcut kararı değiştirmemeye karar verdi.", out.decisions().getFirst().text());
        assertEquals(1, out.actionItems().size());
        assertEquals(0, out.importantFacts().size());
        assertEquals(0, out.openQuestions().size());
        assertEquals(0, out.topics().size());
        assertEquals(0, out.commitments().size());
        assertTrue(out.qualityFlags().contains(CrossTypeMeetingItemScrubber.TYPE_LAUNDER_DROPPED));
    }
}
