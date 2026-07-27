package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TranscriptChunk;

public interface ChunkSignalFeatureExtractor {
    ChunkSignalFeatures extract(TranscriptChunk chunk, ChunkContext context);
}
