package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalNoteAssemblerTest {

    @Test
    void buildsOutcomeFirstSummaryWithoutAgendaDump() {
        List<TopicCandidate> topics = new ArrayList<>();
        for (int i = 1; i <= 17; i++) {
            topics.add(new TopicCandidate("Konu başlığı numarası " + i, List.of("s" + i), 0.9));
        }
        ExtractionBundle bundle = new ExtractionBundle(
                topics,
                List.of(
                        new DecisionCandidate("API sözleşmesini cuma günü donduruyoruz.", List.of("s1"), 0.9),
                        new DecisionCandidate("Varsayılan filtreyi aktif kayıtlar olarak sabitliyoruz.", List.of("s1"), 0.9),
                        new DecisionCandidate("Idempotency anahtarını istemci üretecek.", List.of("s1"), 0.9)
                ),
                List.of(
                        new ActionItemCandidate("VTT regresyon paketini hazırla", "Can", null, List.of("s1"), 0.9),
                        new ActionItemCandidate("Smoke test koş", "Selin", null, List.of("s2"), 0.9),
                        new ActionItemCandidate("Dokümanı paylaş", "Murat", null, List.of("s3"), 0.9),
                        new ActionItemCandidate("Takvimi güncelle", "Ahmet", null, List.of("s4"), 0.9)
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
        assertTrue(summary.startsWith("Sonuçlar:\n1. API sözleşmesini cuma günü donduruyoruz."));
        assertTrue(summary.contains("Sonraki adımlar:"));
        assertTrue(summary.contains("VTT regresyon paketini hazırla (Can)"));
        assertTrue(summary.contains("… +1 aksiyon daha"));
        assertTrue(summary.contains("Dikkat:\n1. Deploy gecikmesi"));
        assertFalse(summary.contains("Gündem:"));
        assertFalse(summary.contains("Konu başlığı numarası"));
        assertFalse(summary.contains("karar kaydedildi"));
        assertEquals(17, draft.topics().size());
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
