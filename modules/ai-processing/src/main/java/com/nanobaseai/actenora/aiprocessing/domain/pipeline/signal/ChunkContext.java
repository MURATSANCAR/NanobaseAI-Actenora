package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import java.util.Optional;

/**
 * Per-chunk gate context (continuation + policy).
 */
public record ChunkContext(
        Optional<ChunkSignalSummary> previous,
        Optional<ChunkSignalSummary> nextPreview,
        SignalGateConfig config
) {
    public ChunkContext {
        previous = previous == null ? Optional.empty() : previous;
        nextPreview = nextPreview == null ? Optional.empty() : nextPreview;
        if (config == null) {
            throw new IllegalArgumentException("config");
        }
    }

    public static ChunkContext of(SignalGateConfig config) {
        return new ChunkContext(Optional.empty(), Optional.empty(), config);
    }

    public static ChunkContext withPrevious(SignalGateConfig config, ChunkSignalSummary previous) {
        return new ChunkContext(Optional.ofNullable(previous), Optional.empty(), config);
    }
}
