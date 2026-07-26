package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

/**
 * Chunk size / overlap bounds. Target shrinks dynamically with model context.
 */
public record ChunkingConfig(
        int minTargetTokens,
        int maxTargetTokens,
        int minOverlapTokens,
        int maxOverlapTokens,
        int modelContextWindowTokens,
        int promptOverheadTokens,
        int maxOutputTokens,
        int safetyMarginTokens
) {
    public ChunkingConfig {
        if (minTargetTokens <= 0 || maxTargetTokens < minTargetTokens) {
            throw new IllegalArgumentException("invalid target token range");
        }
        if (minOverlapTokens < 0 || maxOverlapTokens < minOverlapTokens) {
            throw new IllegalArgumentException("invalid overlap range");
        }
        if (modelContextWindowTokens <= 0) {
            throw new IllegalArgumentException("modelContextWindowTokens must be > 0");
        }
    }

    public static ChunkingConfig productionDefaults(int modelContextWindowTokens) {
        return new ChunkingConfig(
                8_000,
                12_000,
                300,
                500,
                modelContextWindowTokens,
                1_200,
                6_000,
                256
        );
    }

    /**
     * Effective chunk target after reserving prompt, output, and safety budget.
     */
    public int effectiveTargetTokens() {
        int usable = modelContextWindowTokens - promptOverheadTokens - maxOutputTokens - safetyMarginTokens;
        if (usable < minTargetTokens) {
            return Math.max(500, usable);
        }
        return Math.min(maxTargetTokens, Math.max(minTargetTokens, usable));
    }

    public int effectiveOverlapTokens() {
        int target = effectiveTargetTokens();
        int overlap = Math.min(maxOverlapTokens, Math.max(minOverlapTokens, target / 20));
        return Math.min(overlap, Math.max(0, target / 4));
    }

    public ChunkingConfig withMaxOutput(int maxOutputTokens) {
        return new ChunkingConfig(
                minTargetTokens,
                maxTargetTokens,
                minOverlapTokens,
                maxOverlapTokens,
                modelContextWindowTokens,
                promptOverheadTokens,
                maxOutputTokens,
                safetyMarginTokens
        );
    }
}
