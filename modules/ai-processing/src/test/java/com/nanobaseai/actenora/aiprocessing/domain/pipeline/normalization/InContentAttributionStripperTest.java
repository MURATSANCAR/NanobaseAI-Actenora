package com.nanobaseai.actenora.aiprocessing.domain.pipeline.normalization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InContentAttributionStripperTest {

    private final InContentAttributionStripper stripper = new InContentAttributionStripper();

    @Test
    void stripsSelfAttributionBeforeColon() {
        assertEquals(
                "mevcut kararı değiştirmiyoruz.",
                stripper.strip("Can olarak ekliyorum: mevcut kararı değiştirmiyoruz.")
        );
    }

    @Test
    void stripsMultiWordNameAndNotEdiyorum() {
        assertEquals(
                "bu noktayı açalım.",
                stripper.strip("Can Demir olarak not ediyorum: bu noktayı açalım.")
        );
    }

    @Test
    void leavesReportedSpeechUnchanged() {
        String text = "Can mevcut kararı değiştirmiyoruz dedi.";
        assertEquals(text, stripper.strip(text));
    }

    @Test
    void leavesNonSpeechVerbAfterOlarak() {
        String text = "Can olarak ekip lideri kalacak.";
        assertEquals(text, stripper.strip(text));
    }
}
