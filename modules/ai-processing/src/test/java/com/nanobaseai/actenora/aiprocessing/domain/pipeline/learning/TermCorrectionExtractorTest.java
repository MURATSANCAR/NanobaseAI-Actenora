package com.nanobaseai.actenora.aiprocessing.domain.pipeline.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.learning.TermCorrectionExtractor.Correction;
import java.util.List;
import org.junit.jupiter.api.Test;

class TermCorrectionExtractorTest {

    private final TermCorrectionExtractor extractor = new TermCorrectionExtractor();

    @Test
    void learnsSingleWordSubstitutionInSharedContext() {
        List<Correction> c = extractor.extract(
                "Core Banking için Fimple seçildi",
                "Core Banking için Simple seçildi");
        assertEquals(List.of(new Correction("Fimple", "Simple")), c);
    }

    @Test
    void learnsAsrTermFix() {
        assertEquals(List.of(new Correction("invdiya", "NVIDIA")),
                extractor.extract("biz invdiya kullandık", "biz NVIDIA kullandık"));
    }

    @Test
    void learnsTwoWordPhraseSwap() {
        assertEquals(List.of(new Correction("corben king", "Core Banking")),
                extractor.extract("yeni corben king kuruldu", "yeni Core Banking kuruldu"));
    }

    @Test
    void ignoresIdenticalText() {
        assertTrue(extractor.extract("aynı metin burada", "aynı metin burada").isEmpty());
    }

    @Test
    void ignoresLargeRewrite() {
        assertTrue(extractor.extract(
                "toplantı açılışı yapıldı ve herkes tanıştı",
                "tamamen farklı bir konu burada anlatıldı sonra").isEmpty());
    }

    @Test
    void ignoresCommonWordEdits() {
        // "bir" -> "bu" is a stopword edit, must not be learned as a glossary term.
        assertTrue(extractor.extract("bir toplantı yapıldı", "bu toplantı yapıldı").isEmpty());
    }

    @Test
    void nullSafe() {
        assertTrue(extractor.extract(null, "x").isEmpty());
        assertTrue(extractor.extract("x", null).isEmpty());
    }
}
