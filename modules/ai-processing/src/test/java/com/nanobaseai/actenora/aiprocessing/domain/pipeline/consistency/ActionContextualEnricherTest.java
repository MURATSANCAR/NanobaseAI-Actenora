package com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TopicCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ActionContextualEnricherTest {

    @Test
    void qualifiesAnaphoricActionFromNearestPriorTopic() {
        FinalNoteDraft draft = new FinalNoteDraft(
                "Özet",
                List.of(),
                List.of(new ActionItemCandidate(
                        "Düzeltmeyi yapacak.", "Selin", null, List.of("action"), 0.95)),
                List.of(), List.of(), List.of(),
                List.of(
                        new TopicCandidate("Oturum yenileme hatası", List.of("topic-1"), 0.95),
                        new TopicCandidate("E-posta karakter bozulması", List.of("topic-2"), 0.95)
                ),
                List.of(), List.of(), List.of(), List.of(), List.of("action"), 0.95, false
        );
        List<SegmentInput> segments = List.of(
                segment("topic-1", 4, "Gündemde oturum yenileme hatası var."),
                segment("action", 26, "Aksiyon kaydı: Selin düzeltmeyi yapacak."),
                segment("topic-2", 28, "Gündemde e-posta karakter bozulması var.")
        );

        FinalNoteDraft enriched = new ActionContextualEnricher().enrich(draft, segments);

        assertEquals(
                "Oturum yenileme hatası için düzeltmeyi yapacak.",
                enriched.actionItems().getFirst().text()
        );
        assertFalse(ActionContextualEnricher.isGenericAction(
                enriched.actionItems().getFirst().text()));
    }

    private static SegmentInput segment(String id, int sequence, String content) {
        return new SegmentInput(id, sequence, null, sequence * 1000L,
                sequence * 1000L + 500L, content, false);
    }
}
