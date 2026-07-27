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
        assertEquals("s1", normalized.get(0).segmentId());
        assertEquals("s4", normalized.get(1).segmentId());
        assertTrue(normalized.get(0).markerNear());
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
}
