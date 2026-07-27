package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

/**
 * Chunk size / overlap bounds. Target shrinks when model context is tight.
 */
public record ChunkingConfig(
        int targetChunkTokens,
        int maxChunkTokens,
        int minOverlapTokens,
        int maxOverlapTokens,
        int modelContextWindowTokens,
        int promptOverheadTokens,
        int maxOutputTokens,
        int safetyMarginTokens
) {
    public ChunkingConfig {
        if (targetChunkTokens <= 0 || maxChunkTokens < targetChunkTokens) {
            throw new IllegalArgumentException("invalid target/max chunk token range");
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
                MeetingLlmBudgets.TARGET_CHUNK_TOKENS,
                MeetingLlmBudgets.MAX_CHUNK_TOKENS,
                MeetingLlmBudgets.OVERLAP_TOKENS,
                MeetingLlmBudgets.OVERLAP_TOKENS,
                MeetingLlmBudgets.operationalContextWindow(modelContextWindowTokens),
                MeetingLlmBudgets.PROMPT_OVERHEAD_TOKENS,
                MeetingLlmBudgets.EXTRACTION_MAX_TOKENS,
                MeetingLlmBudgets.SAFETY_MARGIN_TOKENS
        );
    }

    /**
     * Effective chunk target after reserving prompt, output, and safety budget.
     * Prefers {@link #targetChunkTokens()} when context allows; never exceeds
     * {@link #maxChunkTokens()}; shrinks when usable context is tight.
     */
    public int effectiveTargetTokens() {
        int usable = modelContextWindowTokens - promptOverheadTokens - maxOutputTokens - safetyMarginTokens;
        if (usable < targetChunkTokens) {
            return Math.max(500, Math.min(maxChunkTokens, usable));
        }
        return Math.min(maxChunkTokens, targetChunkTokens);
    }

    public int effectiveOverlapTokens() {
        int target = effectiveTargetTokens();
        int overlap = Math.min(maxOverlapTokens, Math.max(minOverlapTokens, target / 20));
        return Math.min(overlap, Math.max(0, target / 4));
    }

    public ChunkingConfig withMaxOutput(int maxOutputTokens) {
        return new ChunkingConfig(
                targetChunkTokens,
                maxChunkTokens,
                minOverlapTokens,
                maxOverlapTokens,
                modelContextWindowTokens,
                promptOverheadTokens,
                maxOutputTokens,
                safetyMarginTokens
        );
    }
}
