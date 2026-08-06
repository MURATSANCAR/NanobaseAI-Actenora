package com.nanobaseai.actenora.security.portal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeakerConfidenceAssessmentTest {

    @Test
    void resolvesTitledGraphNameAgainstRoster() {
        var result = SpeakerConfidenceAssessment.assess(
                "Ali BAĞATIR (MÜŞTERİ ÇÖZÜMLERİ GMY)",
                List.of("Ali Bağatır"));

        assertEquals("RESOLVED_ROSTER", result.status());
        assertEquals(0.98d, result.confidence());
        assertFalse(result.reviewRequired());
    }

    @Test
    void keepsGenericAndAsrLabelsInReview() {
        assertTrue(SpeakerConfidenceAssessment.assess("Speaker 1", List.of()).reviewRequired());
        assertTrue(SpeakerConfidenceAssessment.assess("BURAG", List.of("Burak Ayık Kesisoğlu"))
                .reviewRequired());
    }
}
