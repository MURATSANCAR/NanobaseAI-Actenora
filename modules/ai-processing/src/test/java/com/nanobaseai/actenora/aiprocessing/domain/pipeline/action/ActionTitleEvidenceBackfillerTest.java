package com.nanobaseai.actenora.aiprocessing.domain.pipeline.action;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionTitleEvidenceBackfillerTest {

    private final ActionTitleEvidenceBackfiller backfiller = new ActionTitleEvidenceBackfiller();

    @Test
    void repairsTruncatedTitleFromEvidence() {
        ActionItemCandidate truncated = new ActionItemCandidate(
                "Tabanına erişim…",
                "Murat",
                null,
                List.of("seg-db"),
                0.9
        );
        List<SegmentInput> segments = List.of(
                new SegmentInput(
                        "seg-db",
                        1,
                        null,
                        0,
                        1000,
                        "Veri tabanına erişim için okuma yetkisi tanımlanacak.",
                        true
                )
        );
        List<ActionItemCandidate> out = backfiller.backfill(List.of(truncated), segments);
        assertEquals(1, out.size());
        ActionItemCandidate repaired = out.stream()
                .filter(a -> a.evidenceSegmentIds().contains("seg-db"))
                .findFirst()
                .orElseThrow();
        assertTrue(repaired.text().contains("Veri tabanına erişim"));
        assertTrue(repaired.text().length() > truncated.text().length());
    }

    @Test
    void doesNotPasteLongDialogueWhenTitleIsComplete() {
        String complete = "PostgreSQL okuma yetkisini oluştur ve paylaş.";
        ActionItemCandidate action = new ActionItemCandidate(
                complete,
                "Murat",
                null,
                List.of("seg-long"),
                0.9
        );
        List<SegmentInput> segments = List.of(
                new SegmentInput(
                        "seg-long",
                        1,
                        null,
                        0,
                        1000,
                        "Tamam. Evet anladım. Şimdi şöyle yapalım: önce hesap açalım sonra yetki verelim "
                                + "ve ayrıca dokümantasyonu da güncelleyelim diye konuştuk uzun uzun.",
                        true
                )
        );
        List<ActionItemCandidate> out = backfiller.backfill(List.of(action), segments);
        ActionItemCandidate kept = out.stream()
                .filter(a -> a.evidenceSegmentIds().contains("seg-long"))
                .findFirst()
                .orElseThrow();
        assertEquals(complete, kept.text());
    }

    @Test
    void detectsIncompleteTitles() {
        assertTrue(ActionTitleEvidenceBackfiller.needsBackfill("Tabanına erişim…"));
        assertTrue(ActionTitleEvidenceBackfiller.needsBackfill("kısa"));
        assertFalse(ActionTitleEvidenceBackfiller.needsBackfill(
                "Veri tabanına erişim için okuma yetkisi tanımlanacak."));
    }
}
