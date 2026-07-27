package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalNoteAssemblerTest {

    @Test
    void buildsNumberedMultilineSummary() {
        ExtractionBundle bundle = new ExtractionBundle(
                List.of(
                        new TopicCandidate("Sprint planlama ve kapasite", List.of("s1"), 0.9),
                        new TopicCandidate("Ürün gereksinimleri ve filtre davranışı", List.of("s2"), 0.9),
                        new TopicCandidate("Toplantı yönetimi ve teknik ayarlar", List.of("s3"), 0.9)
                ),
                List.of(
                        new DecisionCandidate("A", List.of("s1"), 0.9),
                        new DecisionCandidate("B", List.of("s1"), 0.9),
                        new DecisionCandidate("C", List.of("s1"), 0.9)
                ),
                List.of(
                        new ActionItemCandidate("X", null, null, List.of("s1"), 0.9),
                        new ActionItemCandidate("Y", null, null, List.of("s1"), 0.9)
                ),
                List.of(
                        new RiskCandidate("R1", List.of("s1"), 0.9),
                        new RiskCandidate("R2", List.of("s1"), 0.9)
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("s1", "s2", "s3"),
                0.9d
        );

        FinalNoteDraft draft = new FinalNoteAssembler().assemble(bundle, "tr");
        String summary = draft.executiveSummary();
        assertTrue(summary.startsWith("Gündem:\n1. Sprint planlama ve kapasite\n"));
        assertTrue(summary.contains("2. Ürün gereksinimleri ve filtre davranışı\n"));
        assertTrue(summary.contains("3. Toplantı yönetimi ve teknik ayarlar\n"));
        assertTrue(summary.contains("3 karar kaydedildi."));
        assertTrue(summary.contains("2 aksiyon maddesi."));
        assertTrue(summary.contains("2 risk."));
        assertFalse(summary.contains("; "));
    }
}
