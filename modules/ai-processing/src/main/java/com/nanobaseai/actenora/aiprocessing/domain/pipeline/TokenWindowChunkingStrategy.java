package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.List;
import java.util.Objects;

/**
 * Default production strategy: token-bounded sliding windows of whole VTT segments with
 * marker/signal-aware boundary polish. Delegates to {@link TranscriptChunker}.
 */
public final class TokenWindowChunkingStrategy implements ChunkingStrategy {

    private final TranscriptChunker chunker;

    public TokenWindowChunkingStrategy(TranscriptChunker chunker) {
        this.chunker = Objects.requireNonNull(chunker, "chunker");
    }

    public TokenWindowChunkingStrategy() {
        this(new TranscriptChunker());
    }

    public TokenWindowChunkingStrategy(TokenEstimator tokenEstimator) {
        this(new TranscriptChunker(tokenEstimator));
    }

    @Override
    public List<TranscriptChunk> chunk(List<SegmentInput> segments, ChunkingConfig config) {
        return chunker.chunk(segments, config);
    }
}
