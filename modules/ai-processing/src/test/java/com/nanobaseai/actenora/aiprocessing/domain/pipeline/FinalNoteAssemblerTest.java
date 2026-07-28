package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalNoteAssemblerTest {

    @Test
    void buildsDecisionFirstSummary() {
        ExtractionBundle bundle = new ExtractionBundle(
                List.of(
                        new TopicCandidate("Sprint planlama ve kapasite", List.of("s1"), 0.9),
                        new TopicCandidate("bağlam paylaşımı", List.of("s9"), 0.5)
                ),
                List.of(
                        new DecisionCandidate("API sözleşmesini cuma günü donduruyoruz.", List.of("s1"), 0.9),
                        new DecisionCandidate("Varsayılan filtreyi aktif kayıtlar olarak sabitliyoruz.", List.of("s1"), 0.9),
                        new DecisionCandidate("Idempotency anahtarını istemci üretecek.", List.of("s1"), 0.9)
                ),
                List.of(
                        new ActionItemCandidate("VTT regresyon paketini hazırla", "Can", null, List.of("s1"), 0.9)
                ),
                List.of(new RiskCandidate("Deploy gecikmesi", List.of("s1"), 0.9)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("s1", "s9"),
                0.9d
        );

        FinalNoteDraft draft = new FinalNoteAssembler().assemble(bundle, "tr");
        String summary = draft.executiveSummary();
        assertTrue(summary.startsWith("Kararlar\n1. API sözleşmesini cuma günü donduruyoruz."));
        assertTrue(summary.contains("3. Idempotency anahtarını istemci üretecek."));
        assertTrue(summary.contains("Aksiyonlar\n1. VTT regresyon paketini hazırla"));
        assertTrue(summary.contains("Riskler\n1. Deploy gecikmesi"));
        assertFalse(summary.contains("Gündem:"));
        assertFalse(summary.contains("bağlam paylaşımı"));
        assertFalse(summary.contains("karar kaydedildi"));
    }

    @Test
    void avoidsTopicDumpWhenNoDecisions() {
        ExtractionBundle bundle = new ExtractionBundle(
                List.of(
                        new TopicCandidate("bağlam paylaşımı", List.of("s1"), 0.5),
                        new TopicCandidate("noktanın detaylandırılması", List.of("s2"), 0.5)
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("s1", "s2"),
                0.5d
        );
        FinalNoteDraft draft = new FinalNoteAssembler().assemble(bundle, "tr");
        assertTrue(draft.executiveSummary().contains("güvenilir bir yönetici özeti üretilemedi"));
    }
}
