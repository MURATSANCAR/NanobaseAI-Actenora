package com.nanobaseai.actenora.security.aiprocessing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NanobaseAiBrandSanitizerTest {

    @Test
    void stripsVendorTokens() {
        String out = NanobaseAiBrandSanitizer.sanitize("Failed talking to qwen via vllm/openai at ollama host");
        assertFalse(out.toLowerCase().contains("qwen"));
        assertFalse(out.toLowerCase().contains("vllm"));
        assertFalse(out.toLowerCase().contains("openai"));
        assertFalse(out.toLowerCase().contains("ollama"));
        assertEquals(true, out.contains("NanobaseAI"));
    }

    @Test
    void publicModeHidesStacks() {
        assertEquals("offline", NanobaseAiBrandSanitizer.publicMode("mock"));
        assertEquals("nanobaseai", NanobaseAiBrandSanitizer.publicMode("openai"));
        assertEquals("nanobaseai", NanobaseAiBrandSanitizer.publicMode("vllm"));
    }
}
