package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeetingLlmBudgetsTest {

    @Test
    void productionChunkingPrefersTargetUnderOperationalCtx() {
        ChunkingConfig config = ChunkingConfig.productionDefaults(MeetingLlmBudgets.OPERATIONAL_CTX_SIZE);

        assertEquals(MeetingLlmBudgets.TARGET_CHUNK_TOKENS, config.effectiveTargetTokens());
        assertEquals(MeetingLlmBudgets.OVERLAP_TOKENS, config.effectiveOverlapTokens());
        assertEquals(MeetingLlmBudgets.EXTRACTION_MAX_TOKENS, config.maxOutputTokens());
    }

    @Test
    void chunkBudgetFitsInsideOperationalCtx() {
        ChunkingConfig config = ChunkingConfig.productionDefaults(MeetingLlmBudgets.OPERATIONAL_CTX_SIZE);
        int reserved = config.promptOverheadTokens()
                + config.effectiveTargetTokens()
                + config.maxOutputTokens()
                + config.safetyMarginTokens();

        assertTrue(reserved <= MeetingLlmBudgets.OPERATIONAL_CTX_SIZE);
    }

    @Test
    void operationalClampsNativeModelLimits() {
        assertEquals(MeetingLlmBudgets.OPERATIONAL_CTX_SIZE, MeetingLlmBudgets.operationalContextWindow(32_768));
        assertEquals(MeetingLlmBudgets.DEFAULT_MAX_TOKENS, MeetingLlmBudgets.operationalMaxOutput(6_000));
        assertEquals(512, MeetingLlmBudgets.operationalMaxOutput(512));
    }

    @Test
    void productionDefaultsClampsNative32kContext() {
        ChunkingConfig config = ChunkingConfig.productionDefaults(32_768);
        assertEquals(MeetingLlmBudgets.OPERATIONAL_CTX_SIZE, config.modelContextWindowTokens());
        assertEquals(MeetingLlmBudgets.TARGET_CHUNK_TOKENS, config.effectiveTargetTokens());
    }
}
