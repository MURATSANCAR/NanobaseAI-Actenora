package com.nanobaseai.actenora.meetingintelligence.application.mapping;

import com.nanobaseai.actenora.meetingintelligence.domain.model.QualityFlagCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PipelineQualityFlagCodeMappingTest {

    @Test
    void mapsSynthesisAndAuditFallbackTokens() {
        assertEquals(
                QualityFlagCode.SYNTHESIS_FALLBACK,
                MapAiCandidatesToNoteService.resolvePipelineQualityFlagCode("SYNTHESIS_FALLBACK")
        );
        assertEquals(
                QualityFlagCode.AUDIT_FALLBACK,
                MapAiCandidatesToNoteService.resolvePipelineQualityFlagCode("audit_fallback")
        );
        assertEquals(
                QualityFlagCode.OTHER,
                MapAiCandidatesToNoteService.resolvePipelineQualityFlagCode("LLM_SYNTHESIZED")
        );
    }
}
