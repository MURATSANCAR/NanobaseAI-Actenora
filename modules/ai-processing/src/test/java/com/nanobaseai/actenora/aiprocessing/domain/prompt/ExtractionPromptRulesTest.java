package com.nanobaseai.actenora.aiprocessing.domain.prompt;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteAssembler;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractionPromptRulesTest {

    @Test
    void systemRulesBindOutputLanguagePlaceholders() {
        String tr = ExtractionPromptRules.systemRulesFor("tr");
        assertTrue(tr.contains("Türkçe"));
        assertTrue(tr.contains("(dil kodu: tr)"));
        assertFalse(tr.contains("{{outputLanguage}}"));
        assertTrue(tr.contains("Meeting extraction completed"));

        String en = ExtractionPromptRules.systemRulesFor("en-US");
        assertTrue(en.contains("English"));
        assertTrue(en.contains("(dil kodu: en)"));
        assertTrue(en.contains("MUST be English only"));
    }

    @Test
    void emptyAssemblerSummaryFollowsLanguage() {
        FinalNoteAssembler assembler = new FinalNoteAssembler();
        FinalNoteDraft tr = assembler.assemble(ExtractionBundle.empty(), "tr");
        FinalNoteDraft en = assembler.assemble(ExtractionBundle.empty(), "en");
        assertTrue(tr.executiveSummary().contains("Çıkarım tamamlandı"));
        assertTrue(en.executiveSummary().contains("Extraction completed"));
        assertEquals("tr", ExtractionPromptRules.normalizeLanguage(null));
    }

    @Test
    void sanitizesEnglishMetaWhenOutputLanguageIsTurkish() {
        String cleaned = OutputLanguagePolicy.sanitizeUserFacingText(
                "Meeting extraction completed with no primary topics.",
                "tr"
        );
        assertTrue(cleaned.contains("Çıkarım tamamlandı"));
        assertFalse(cleaned.toLowerCase().contains("meeting extraction"));
    }

    @Test
    void prefersPrimaryLanguageOverFallback() {
        assertEquals("tr", OutputLanguagePolicy.firstNonBlank("tr-TR", "en"));
        assertEquals("en", OutputLanguagePolicy.firstNonBlank(null, "en-US"));
        assertEquals("tr", OutputLanguagePolicy.firstNonBlank(" ", " "));
    }
}
