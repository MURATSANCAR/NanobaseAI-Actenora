package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import java.util.Optional;

/**
 * Per-chunk gate context (continuation + policy + language).
 */
public record ChunkContext(
        Optional<ChunkSignalSummary> previous,
        Optional<ChunkSignalSummary> nextPreview,
        SignalGateConfig config,
        String language
) {
    public ChunkContext {
        previous = previous == null ? Optional.empty() : previous;
        nextPreview = nextPreview == null ? Optional.empty() : nextPreview;
        if (config == null) {
            throw new IllegalArgumentException("config");
        }
        language = language == null || language.isBlank() ? "tr" : language.trim().toLowerCase();
    }

    public static ChunkContext of(SignalGateConfig config) {
        return new ChunkContext(Optional.empty(), Optional.empty(), config, "tr");
    }

    public static ChunkContext of(SignalGateConfig config, String language) {
        return new ChunkContext(Optional.empty(), Optional.empty(), config, language);
    }

    public static ChunkContext withPrevious(SignalGateConfig config, ChunkSignalSummary previous) {
        return new ChunkContext(Optional.ofNullable(previous), Optional.empty(), config, "tr");
    }

    public static ChunkContext withNeighbors(
            SignalGateConfig config,
            String language,
            ChunkSignalSummary previous,
            ChunkSignalSummary nextPreview
    ) {
        return new ChunkContext(
                Optional.ofNullable(previous),
                Optional.ofNullable(nextPreview),
                config,
                language
        );
    }
}
