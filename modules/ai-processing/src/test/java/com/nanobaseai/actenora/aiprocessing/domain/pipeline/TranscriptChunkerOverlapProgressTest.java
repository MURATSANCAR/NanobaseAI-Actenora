package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: marker-preferring overlap must not rewind to the previous chunk start
 * (that path allocated until OOM on production EVAL meetings).
 */
class TranscriptChunkerOverlapProgressTest {

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void markerAtChunkHeadDoesNotInfiniteLoop() {
        List<SegmentInput> segments = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            boolean marker = i % 5 == 0;
            String content = marker
                    ? "Karar: sprint scope " + i + " için onaylandı ve aksiyon planlandı"
                    : "Konuşma içeriği segment " + i + " ürün gereksinimi ve kapasite tartışması";
            segments.add(new SegmentInput(
                    "s-" + i, i, "Speaker", i * 1000L, i * 1000L + 900L, content, marker));
        }

        ChunkingConfig config = ChunkingConfig.productionDefaults(16_384)
                .withMaxOutput(MeetingLlmBudgets.EXTRACTION_MAX_TOKENS);
        List<TranscriptChunk> chunks = new TranscriptChunker().chunk(segments, config);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() < segments.size(), "chunks=" + chunks.size());
        int covered = chunks.stream().mapToInt(c -> c.segments().size()).sum();
        assertTrue(covered >= segments.size(), "overlap may re-cover but must finish");
    }
}
