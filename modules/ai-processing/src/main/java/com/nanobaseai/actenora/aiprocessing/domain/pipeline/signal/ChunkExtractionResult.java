package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;

import java.util.Objects;

public record ChunkExtractionResult(
        ExtractionBundle bundle,
        ChunkGateDecision gateDecision,
        boolean skippedWithoutInfer
) {
    public ChunkExtractionResult {
        Objects.requireNonNull(bundle, "bundle");
        Objects.requireNonNull(gateDecision, "gateDecision");
    }

    public static ChunkExtractionResult skipped(ChunkGateDecision decision, ExtractionBundle empty) {
        return new ChunkExtractionResult(empty, decision, true);
    }

    public static ChunkExtractionResult extracted(ExtractionBundle bundle, ChunkGateDecision decision) {
        return new ChunkExtractionResult(bundle, decision, false);
    }
}
