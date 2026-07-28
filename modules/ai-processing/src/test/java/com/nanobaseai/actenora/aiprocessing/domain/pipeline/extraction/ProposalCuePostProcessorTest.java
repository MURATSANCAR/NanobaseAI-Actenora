package com.nanobaseai.actenora.aiprocessing.domain.pipeline.extraction;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ProposalCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TopicCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProposalCuePostProcessorTest {

    private final ProposalCuePostProcessor processor = ProposalCuePostProcessor.productionDefaults();

    @Test
    void movesHenuzKararDegilFromDecisionAndFactToProposals() {
        ExtractionBundle input = new ExtractionBundle(
                List.of(new TopicCandidate("Belki sprinte erteleyelim henüz karar değil", List.of("t1"), 0.9)),
                List.of(new DecisionCandidate(
                        "Bu öneriyi not ediyorum ama henüz karar değil.", List.of("d1"), 0.9)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ImportantFactCandidate(
                        "Önerim önce küçük bir spike; henüz karar değil.", List.of("f1"), 0.85)),
                List.of(),
                List.of("d1", "f1", "t1"),
                0.9
        );
        ExtractionBundle out = processor.process(input);
        assertEquals(0, out.decisions().size());
        assertEquals(0, out.importantFacts().size());
        assertEquals(0, out.topics().size());
        assertEquals(3, out.proposals().size());
        assertTrue(out.proposals().stream().allMatch(p -> p.text().toLowerCase().contains("henüz karar değil")
                || p.text().toLowerCase().contains("öner")));
    }

    @Test
    void seedsDroppedProposalCuesFromSegments() {
        ExtractionBundle empty = new ExtractionBundle(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of("s6", "s21", "s36", "s20"), 0.5
        );
        List<SegmentInput> segments = List.of(
                seg("s6", "Bu öneriyi not ediyorum ama henüz karar değil."),
                seg("s20", "Önerim, önce küçük bir spike yapmamız yönünde."),
                seg("s21", "Bu öneriyi not ediyorum ama henüz karar değil."),
                seg("s36", "Bu öneriyi not ediyorum ama henüz karar değil."),
                seg("s99", "Bu noktayı biraz açmamız iyi olur.")
        );
        ExtractionBundle out = processor.process(empty, segments);
        assertEquals(4, out.proposals().size());
        assertTrue(out.proposals().stream().anyMatch(p -> p.text().contains("spike")));
        assertEquals(3, out.proposals().stream()
                .filter(p -> p.text().toLowerCase().contains("henüz karar değil"))
                .count());
    }

    private static SegmentInput seg(String id, String content) {
        return new SegmentInput(id, 1, "Speaker", 0, 1000, content, false);
    }

    @Test
    void doesNotPromoteVagueDiscussionPrompt() {
        ExtractionBundle input = new ExtractionBundle(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new OpenQuestionCandidate("Bu noktayı biraz açmamız iyi olur.", List.of("q1"), 0.8)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("q1"),
                0.8
        );
        ExtractionBundle out = processor.process(input);
        assertEquals(0, out.proposals().size());
        assertEquals(1, out.openQuestions().size());
    }

    @Test
    void keepsExplicitDecisions() {
        ExtractionBundle input = new ExtractionBundle(
                List.of(),
                List.of(new DecisionCandidate("API sözleşmesini cuma günü donduruyoruz.", List.of("d1"), 0.95)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ProposalCandidate("Spike yapalım, henüz karar değil.", List.of("p1"), 0.9)),
                List.of(),
                List.of(),
                List.of("d1", "p1"),
                0.9
        );
        ExtractionBundle out = processor.process(input);
        assertEquals(1, out.decisions().size());
        assertEquals(1, out.proposals().size());
    }
}
