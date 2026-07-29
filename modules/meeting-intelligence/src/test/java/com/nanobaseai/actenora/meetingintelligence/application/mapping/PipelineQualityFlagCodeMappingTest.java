package com.nanobaseai.actenora.meetingintelligence.application.mapping;

import com.nanobaseai.actenora.meetingintelligence.domain.model.QualityFlagCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                QualityFlagCode.TYPE_LAUNDER_DROPPED,
                MapAiCandidatesToNoteService.resolvePipelineQualityFlagCode("TYPE_LAUNDER_DROPPED")
        );
        assertEquals(
                QualityFlagCode.OTHER,
                MapAiCandidatesToNoteService.resolvePipelineQualityFlagCode("LLM_SYNTHESIZED")
        );
    }

    @Test
    void falseFallbackTelemetryDoesNotTriggerManualReview() {
        assertFalse(MapAiCandidatesToNoteService.requiresManualReview(
                false,
                List.of("fallbackUsed=false", "auditStatus=PASSED")
        ));
        assertTrue(MapAiCandidatesToNoteService.requiresManualReview(
                false,
                List.of("FINALIZATION_FALLBACK")
        ));
    }
}
