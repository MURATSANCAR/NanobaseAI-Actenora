package com.nanobaseai.actenora.aiprocessing.domain.pipeline.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GlossaryMatcherTest {

    private static GlossaryMatcher matcher() {
        return new GlossaryMatcher(Map.of(
                "invdiya", "NVIDIA",
                "api", "API",
                "türk telekom", "Türk Telekom",
                "google", "Google",
                "on prem", "on-prem"
        ));
    }

    @Test
    void rewritesSingleWordSurface() {
        assertEquals("biz NVIDIA kullanıyoruz", matcher().rewrite("biz invdiya kullanıyoruz"));
    }

    @Test
    void doesNotMatchInsideWord() {
        // "apiler" is a single token — must not become "APIler".
        assertEquals("apiler güzel bir kelime", matcher().rewrite("apiler güzel bir kelime"));
    }

    @Test
    void matchesWordAcrossApostropheSuffix() {
        // Turkish suffix via apostrophe: only the acronym token is normalized, suffix kept.
        assertEquals("bir API'ler var", matcher().rewrite("bir api'ler var"));
    }

    @Test
    void longestMultiWordPhraseWins() {
        assertEquals("Türk Telekom ile görüştük", matcher().rewrite("türk telekom ile görüştük"));
    }

    @Test
    void caseInsensitiveAndCanonicalCasing() {
        assertEquals("Google ve Google", matcher().rewrite("GOOGLE ve google"));
    }

    @Test
    void leavesAlreadyCanonicalTextUnchanged() {
        String in = "Google zaten doğru yazılmış.";
        assertEquals(in, matcher().rewrite(in));
    }

    @Test
    void multiWordCanonicalWithHyphen() {
        assertEquals("on-prem çözüm tercih edildi", matcher().rewrite("on prem çözüm tercih edildi"));
    }

    @Test
    void emptyGlossaryIsNoop() {
        assertEquals("değişmez metin", new GlossaryMatcher(Map.of()).rewrite("değişmez metin"));
    }

    @Test
    void scalesToLargeGlossary() {
        // 100k synthetic entries must not blow up a single short rewrite (O(text), not O(terms)).
        java.util.Map<String, String> big = new java.util.HashMap<>();
        for (int i = 0; i < 100_000; i++) {
            big.put("terimx" + i, "TERIMX" + i);
        }
        big.put("invdiya", "NVIDIA");
        GlossaryMatcher m = new GlossaryMatcher(big);
        assertEquals("bir NVIDIA cümlesi", m.rewrite("bir invdiya cümlesi"));
    }
}
