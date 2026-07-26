package com.nanobaseai.actenora.security.aiprocessing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NanobaseAiBrandSanitizerTest {

    @Test
    void stripsVendorTokens() {
        String out = NanobaseAiBrandSanitizer.sanitize("Failed talking to qwen via vllm/openai at ollama host");
        assertFalse(out.toLowerCase().contains("qwen"));
        assertFalse(out.toLowerCase().contains("vllm"));
        assertFalse(out.toLowerCase().contains("openai"));
        assertFalse(out.toLowerCase().contains("ollama"));
        assertTrue(out.contains("NanobaseAI EasyMeeting"));
    }

    @Test
    void replacesLlmWithProductBrand() {
        String out = NanobaseAiBrandSanitizer.sanitize("Taslak (LLM)");
        assertFalse(out.toUpperCase().contains("LLM"));
        assertEquals(NanobaseAiBrandSanitizer.draftStatusLabel(), out);
    }

    @Test
    void publicModeHidesStacks() {
        assertEquals("offline", NanobaseAiBrandSanitizer.publicMode("mock"));
        assertEquals("nanobaseai", NanobaseAiBrandSanitizer.publicMode("openai"));
        assertEquals("nanobaseai", NanobaseAiBrandSanitizer.publicMode("vllm"));
    }

    @Test
    void displayModelNameNeverExposesVendors() {
        assertEquals(
                NanobaseAiBrandSanitizer.PRODUCT_DISPLAY_NAME,
                NanobaseAiBrandSanitizer.displayModelName("qwen2.5-27b")
        );
    }
}
