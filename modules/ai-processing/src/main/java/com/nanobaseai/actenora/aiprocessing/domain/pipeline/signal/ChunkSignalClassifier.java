package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TranscriptChunk;

/**
 * Optional secondary classifier used only when the deterministic gate is uncertain.
 * Deterministic strong/skip decisions do not call this.
 */
public interface ChunkSignalClassifier {

    SignalStrength classify(
            TranscriptChunk chunk,
            ChunkContext context,
            ChunkSignalFeatures features,
            double normalizedScore
    );
}
