package com.nanobaseai.actenora.aiprocessing.domain.pipeline.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MeetingTerminologyNormalizerCsvTest {

    @Test
    void loadsBaseGlossaryFromClasspathCsv() {
        int size = MeetingTerminologyNormalizer
                .loadFromResource("/aiprocessing/glossary/base-glossary.csv").size();
        assertTrue(size > 100, "base glossary should load 100+ entries, got " + size);
    }

    @Test
    void productionDefaultsRewritesKnownTerms() {
        MeetingTerminologyNormalizer n = MeetingTerminologyNormalizer.productionDefaults();
        assertEquals("Core Banking için Simple seçildi",
                n.rewrite("Core Banking için Fimple seçildi"));
        assertEquals("biz NVIDIA kullanıyoruz", n.rewrite("biz invdiya kullanıyoruz"));
        assertEquals("Türk Telekom ile", n.rewrite("türk telekom ile"));
        assertEquals("on-prem çözüm", n.rewrite("on prem çözüm"));
    }

    @Test
    void fallbackWhenResourceMissing() {
        // Missing resource must not crash — falls back to a minimal safe set.
        assertTrue(MeetingTerminologyNormalizer
                .loadFromResource("/aiprocessing/glossary/does-not-exist.csv").size() >= 1);
    }
}
