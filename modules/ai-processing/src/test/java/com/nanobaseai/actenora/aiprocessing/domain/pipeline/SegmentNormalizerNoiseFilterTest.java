package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentNormalizerNoiseFilterTest {

    @Test
    void dropsFillerButKeepsSignalSegments() {
        SegmentNormalizer normalizer = new SegmentNormalizer();
        List<SegmentInput> normalized = normalizer.normalize(List.of(
                new SegmentInput("s1", 0, "Emre", 0, 1_000,
                        "Karar: API sözleşmesini cuma günü donduruyoruz.", false),
                new SegmentInput("s2", 1, "Emre", 1_000, 2_000,
                        "Mikrofonumu açıyorum", false),
                new SegmentInput("s3", 2, "Elif", 2_000, 3_000,
                        "Bu arada ekrandaki madde listesini senkronize ediyorum, yeni karar yok.", false),
                new SegmentInput("s4", 3, "Can", 3_000, 4_000,
                        "Risk için erken smoke yapacağız.", false)
        ));
        assertEquals(2, normalized.size());
        assertTrue(normalized.stream().anyMatch(s -> "s1".equals(s.segmentId()) && s.markerNear()));
        assertTrue(normalized.stream().anyMatch(s -> "s4".equals(s.segmentId())));
        assertTrue(normalized.stream().noneMatch(s -> "s2".equals(s.segmentId()) || "s3".equals(s.segmentId())));
    }

    @Test
    void doesNotEmptyTranscriptWhenAllNoise() {
        SegmentNormalizer normalizer = new SegmentNormalizer();
        List<SegmentInput> normalized = normalizer.normalize(List.of(
                new SegmentInput("s1", 0, "Emre", 0, 1_000, "Mikrofonumu açıyorum", false),
                new SegmentInput("s2", 1, "Emre", 1_000, 2_000, "Anladım, teşekkürler", false)
        ));
        assertEquals(2, normalized.size());
    }

    @Test
    void stripsAttributionThenDropsStatusQuoFiller() {
        SegmentNormalizer normalizer = new SegmentNormalizer();
        List<SegmentInput> normalized = normalizer.normalize(List.of(
                new SegmentInput("s1", 0, "Can", 0, 1_000,
                        "Can olarak ekliyorum: mevcut kararı değiştirmiyoruz, sadece bağlam paylaşıyorum.",
                        false),
                new SegmentInput("s2", 1, "Elif", 1_000, 2_000,
                        "Kararlaştırdık: API sözleşmesini cuma günü donduruyoruz.", false),
                new SegmentInput("s3", 2, "Selin", 2_000, 3_000,
                        "Bu noktayı biraz açmamız iyi olur.", false)
        ));
        assertEquals(1, normalized.size());
        assertEquals("s2", normalized.getFirst().segmentId());
    }
}
