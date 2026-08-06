package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenWindowChunkingStrategyTest {

    @Test
    void defaultStrategyMatchesTranscriptChunkerPacking() {
        List<SegmentInput> segments = List.of(
                seg("a", "Karar: API sözleşmesi cuma dondurulacak ve ekip bilgilendirilecek."),
                seg("b", "Aksiyon: Murat PDF sunumunu paylaşacak."),
                seg("c", "Risk: BDDK veri çıkışı kısıtı değerlendirilecek.")
        );
        ChunkingConfig config = ChunkingConfig.productionDefaults(16_384);
        List<TranscriptChunk> viaStrategy = new TokenWindowChunkingStrategy().chunk(segments, config);
        List<TranscriptChunk> viaChunker = new TranscriptChunker().chunk(segments, config);
        assertEquals(viaChunker.size(), viaStrategy.size());
        assertEquals(viaChunker.getFirst().segmentIds(), viaStrategy.getFirst().segmentIds());
        assertTrue(viaStrategy.getFirst().estimatedTokens() > 0);
    }

    private static SegmentInput seg(String id, String text) {
        return new SegmentInput(id, 1, "Speaker", 0, 1000, text, false);
    }
}
