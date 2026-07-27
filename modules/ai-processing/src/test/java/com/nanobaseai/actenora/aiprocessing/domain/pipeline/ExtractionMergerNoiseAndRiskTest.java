package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtractionMergerNoiseAndRiskTest {

    @Test
    void mergeStripsStatusQuoDecisionsAndPrefersRiskMitigation() {
        ExtractionBundle a = new ExtractionBundle(
                List.of(),
                List.of(new DecisionCandidate(
                        "Mevcut kararı değiştirmiyoruz, sadece bağlam", List.of("s1"), 0.8)),
                List.of(),
                List.of(new RiskCandidate(
                        "Deploy gecikmesi 15000 TL", List.of("s2"), 0.9, "HIGH", null)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("s1", "s2"),
                0.8
        );
        ExtractionBundle b = new ExtractionBundle(
                List.of(),
                List.of(new DecisionCandidate(
                        "Varsayılan filtre aktif kayıtlar olacak", List.of("s3"), 0.95)),
                List.of(),
                List.of(new RiskCandidate(
                        "Deploy gecikmesi 15000 TL", List.of("s2"), 0.9, "HIGH", "erken smoke")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("s2", "s3"),
                0.9
        );

        ExtractionBundle merged = new ExtractionMerger().merge(List.of(a, b));
        assertEquals(2, merged.decisions().size());
        assertEquals(1, merged.risks().size());
        assertEquals("erken smoke", merged.risks().getFirst().mitigation());
        assertEquals("HIGH", merged.risks().getFirst().likelihood());
    }

    @Test
    void assembleAlsoStripsStatusQuo() {
        ExtractionBundle bundle = new ExtractionBundle(
                List.of(),
                List.of(
                        new DecisionCandidate("API cuma dondurulacak", List.of("s1"), 0.9),
                        new DecisionCandidate("Yeni karar yok bu turda", List.of("s2"), 0.7)
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("s1", "s2"),
                0.85
        );
        FinalNoteDraft draft = new FinalNoteAssembler().assemble(bundle);
        assertEquals(2, draft.decisions().size());
    }
}
