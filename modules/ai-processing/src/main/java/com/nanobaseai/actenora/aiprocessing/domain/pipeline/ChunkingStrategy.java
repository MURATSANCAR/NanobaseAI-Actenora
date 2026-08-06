package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.List;

/**
 * Pluggable transcript chunking. Production default is token-window packing of whole segments
 * ({@link TokenWindowChunkingStrategy}). Topic-aware strategies (e.g. TreeSeg-inspired) may be
 * added later without changing evidence-id / EXTRACT-by-index contracts — they must remain
 * deterministic and segment-atomic.
 */
public interface ChunkingStrategy {

    List<TranscriptChunk> chunk(List<SegmentInput> segments, ChunkingConfig config);
}
