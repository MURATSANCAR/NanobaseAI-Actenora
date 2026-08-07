package com.nanobaseai.actenora.aiprocessing.infrastructure.chunking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ChunkingConfig;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ChunkingStrategy;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TranscriptChunk;
import com.nanobaseai.actenora.aiprocessing.infrastructure.chunking.SemanticChunkingStrategy.SemanticChunkingException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SemanticChunkingStrategyTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static SegmentInput seg(String id, int seq, String text) {
        return new SegmentInput(id, seq, "Speaker", seq * 1000L, seq * 1000L + 500L, text, false);
    }

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void mapsTwoTopicChunks() {
        List<SegmentInput> segs = List.of(
                seg("a", 0, "kredi riski"),
                seg("b", 1, "sermaye yeterliligi"),
                seg("c", 2, "ogle yemegi"),
                seg("d", 3, "tatli baklava"));
        JsonNode resp = parse("{\"chunks\":["
                + "{\"segment_ids\":[\"a\",\"b\"]},"
                + "{\"segment_ids\":[\"c\",\"d\"]}]}");
        List<TranscriptChunk> chunks = SemanticChunkingStrategy.mapResponse(segs, resp);
        assertEquals(2, chunks.size());
        assertEquals(List.of("a", "b"), chunks.get(0).segmentIds());
        assertEquals(List.of("c", "d"), chunks.get(1).segmentIds());
        assertEquals(0, chunks.get(0).index());
        assertEquals(1, chunks.get(1).index());
    }

    @Test
    void rejectsIncompleteCoverage() {
        List<SegmentInput> segs = List.of(seg("a", 0, "x"), seg("b", 1, "y"), seg("c", 2, "z"));
        JsonNode resp = parse("{\"chunks\":[{\"segment_ids\":[\"a\",\"b\"]}]}");
        assertThrows(SemanticChunkingException.class,
                () -> SemanticChunkingStrategy.mapResponse(segs, resp));
    }

    @Test
    void rejectsReorderedCoverage() {
        List<SegmentInput> segs = List.of(seg("a", 0, "x"), seg("b", 1, "y"));
        JsonNode resp = parse("{\"chunks\":[{\"segment_ids\":[\"b\",\"a\"]}]}");
        assertThrows(SemanticChunkingException.class,
                () -> SemanticChunkingStrategy.mapResponse(segs, resp));
    }

    @Test
    void rejectsUnknownSegment() {
        List<SegmentInput> segs = List.of(seg("a", 0, "x"));
        JsonNode resp = parse("{\"chunks\":[{\"segment_ids\":[\"zzz\"]}]}");
        assertThrows(SemanticChunkingException.class,
                () -> SemanticChunkingStrategy.mapResponse(segs, resp));
    }

    @Test
    void rejectsNoChunks() {
        List<SegmentInput> segs = List.of(seg("a", 0, "x"));
        assertThrows(SemanticChunkingException.class,
                () -> SemanticChunkingStrategy.mapResponse(segs, parse("{\"chunks\":[]}")));
    }

    @Test
    void disabledDelegatesToFallback() {
        ChunkingConfig config = ChunkingConfig.productionDefaults(32768);
        List<SegmentInput> segs = List.of(seg("a", 0, "x"), seg("b", 1, "y"));
        List<TranscriptChunk> sentinel = List.of(new TranscriptChunk(0, segs, 4));
        ChunkingStrategy fallback = (s, c) -> sentinel;
        SemanticChunkingStrategy strategy = new SemanticChunkingStrategy(
                fallback, "http://unused:8000", false, 90.0, Duration.ofSeconds(5));
        assertSame(sentinel, strategy.chunk(segs, config));
    }

    @Test
    void blankUrlDelegatesToFallback() {
        ChunkingConfig config = ChunkingConfig.productionDefaults(32768);
        List<SegmentInput> segs = List.of(seg("a", 0, "x"), seg("b", 1, "y"));
        List<TranscriptChunk> sentinel = List.of(new TranscriptChunk(0, segs, 4));
        ChunkingStrategy fallback = (s, c) -> sentinel;
        SemanticChunkingStrategy strategy = new SemanticChunkingStrategy(
                fallback, "  ", true, 90.0, Duration.ofSeconds(5));
        assertSame(sentinel, strategy.chunk(segs, config));
    }
}
