package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingNoisePatternsTest {

    @Test
    void dropsLowSignalOpsAndStatusQuoSegments() {
        assertTrue(MeetingNoisePatterns.isLowSignalSegment("Mikrofonumu açıyorum"));
        assertTrue(MeetingNoisePatterns.isLowSignalSegment("Ekranı paylaşıyorum, yeni karar yok"));
        assertTrue(MeetingNoisePatterns.isLowSignalSegment(
                "Bu arada ekrandaki madde listesini senkronize ediyorum, yeni karar yok."));
        assertTrue(MeetingNoisePatterns.isLowSignalSegment(
                "Mevcut kararı değiştirmiyoruz, sadece bağlam paylaşımı."));
        assertTrue(MeetingNoisePatterns.isLowSignalSegment(
                "mevcut kararı değiştirmiyoruz, sadece bağlam paylaşıyorum."));
        assertTrue(MeetingNoisePatterns.isLowSignalSegment("Bu noktayı biraz açmamız iyi olur."));
        assertTrue(MeetingNoisePatterns.isLowSignalSegment("Bu konuyu biraz açalım."));
        assertTrue(MeetingNoisePatterns.isLowSignalSegment("Anladım, teşekkürler"));
        assertFalse(MeetingNoisePatterns.isLowSignalSegment(
                "API sözleşmesini cuma günü donduruyoruz, Can VTT paketini gelecek hafta Cuma'ya kadar bitirecek."));
        assertFalse(MeetingNoisePatterns.isLowSignalSegment(
                "Risk için erken smoke yapacağız. Yeni karar yok."));
    }

    @Test
    void stripsStatusQuoFalseDecisions() {
        assertTrue(MeetingNoisePatterns.isStatusQuoNonDecision(
                "Mevcut kararı değiştirmiyoruz; sadece bağlam paylaşımı yapıyoruz."));
        assertTrue(MeetingNoisePatterns.isStatusQuoNonDecision("Bu turda yeni karar yok."));
        assertFalse(MeetingNoisePatterns.isStatusQuoNonDecision(
                "API sözleşmesi cuma günü dondurulacak."));

        ExtractionBundle bundle = new ExtractionBundle(
                List.of(),
                List.of(
                        new DecisionCandidate("API sözleşmesi cuma dondurulacak", List.of("s1"), 0.9),
                        new DecisionCandidate(
                                "Mevcut kararı değiştirmiyoruz, sadece bağlam paylaşımı", List.of("s2"), 0.8)
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
        ExtractionBundle cleaned = MeetingNoisePatterns.stripStatusQuoDecisions(bundle);
        assertEquals(1, cleaned.decisions().size());
        assertEquals("API sözleşmesi cuma dondurulacak", cleaned.decisions().getFirst().text());
    }
}
