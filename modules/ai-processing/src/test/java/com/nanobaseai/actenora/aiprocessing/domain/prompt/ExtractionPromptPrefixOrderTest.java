package com.nanobaseai.actenora.aiprocessing.domain.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractionPromptPrefixOrderTest {

    @Test
    void systemRulesKeepStablePrefixBeforeLanguageOverlay() {
        String rules = ExtractionPromptRules.systemRulesFor("tr");
        int schemaIdx = rules.indexOf("OUTPUT SCHEMA");
        int kritikIdx = rules.indexOf("KRİTİK:");
        assertTrue(schemaIdx > 0, "v2 prompt should include OUTPUT SCHEMA block");
        assertTrue(kritikIdx > schemaIdx, "language overlay must follow fixed system prefix");
    }
}
