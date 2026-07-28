package com.nanobaseai.actenora.aiprocessing.domain.pipeline.note;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.MeetingQualityProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalNoteConfidencePolicyTest {

    @Test
    void capsDoubleFallbackAndForcesManualReview() {
        FinalNoteDraft draft = new FinalNoteDraft(
                "summary",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("SYNTHESIS_FALLBACK", "AUDIT_FALLBACK"),
                List.of(),
                0.91d,
                false
        );
        FinalNoteDraft out = new FinalNoteConfidencePolicy(MeetingQualityProperties.defaults()).apply(draft);
        assertEquals(0.45d, out.confidence(), 0.0001);
        assertTrue(out.requiresManualReview());
        assertTrue(out.qualityFlags().contains("REQUIRES_MANUAL_REVIEW"));
    }
}
