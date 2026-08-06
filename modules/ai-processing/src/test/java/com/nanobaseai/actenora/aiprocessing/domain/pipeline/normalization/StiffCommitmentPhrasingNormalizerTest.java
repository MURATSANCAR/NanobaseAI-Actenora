package com.nanobaseai.actenora.aiprocessing.domain.pipeline.normalization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StiffCommitmentPhrasingNormalizerTest {

    @Test
    void rewritesInfinitiveTaahhutEtmistirToNaturalBelirtti() {
        String in = "Müşteri faaliyet izni için gerekli ekip ve teknoloji altyapısını oluşturmayı taahhüt etmiştir.";
        String out = StiffCommitmentPhrasingNormalizer.soften(in);
        assertEquals(
                "Müşteri faaliyet izni için gerekli ekip ve teknoloji altyapısını oluşturacağını belirtti.",
                out
        );
        assertFalse(out.toLowerCase().contains("taahhüt"));
    }

    @Test
    void rewritesInfinitiveTaahhutEttiWithYBuffer() {
        String out = StiffCommitmentPhrasingNormalizer.soften("Burak test planını eklemeyi taahhüt etti.");
        assertEquals("Burak test planını ekleyeceğini belirtti.", out);
    }

    @Test
    void rewritesGondermeyi() {
        assertEquals(
                "PDF sunumu göndereceğini belirtti.",
                StiffCommitmentPhrasingNormalizer.soften("PDF sunumu göndermeyi taahhüt etti.")
        );
    }

    @Test
    void rewritesBareTaahhutEtti() {
        assertEquals("Müşteri belirtti.", StiffCommitmentPhrasingNormalizer.soften("Müşteri taahhüt etti."));
    }

    @Test
    void leavesNaturalFutureTenseAlone() {
        String natural = "Onur PDF sunumu gönderecek.";
        assertEquals(natural, StiffCommitmentPhrasingNormalizer.soften(natural));
    }
}
