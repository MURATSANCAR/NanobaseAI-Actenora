package com.nanobaseai.actenora.security.aiprocessing;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Strips third-party technology vendor tokens from user-facing text.
 * Public product surface is NanobaseAI EasyMeeting.
 */
public final class NanobaseAiBrandSanitizer {

    public static final String PRODUCT_DISPLAY_NAME = "NanobaseAI EasyMeeting";

    private static final Pattern VENDOR = Pattern.compile(
            "(?i)\\b("
                    + "llm|"
                    + "qwen(?:[\\d._-]*)?|"
                    + "ollama|"
                    + "vllm|"
                    + "llama(?:\\.?cpp)?|"
                    + "openai|"
                    + "anthropic|"
                    + "claude|"
                    + "chatgpt|"
                    + "gpt-?\\d*|"
                    + "mistral|"
                    + "huggingface|"
                    + "transformers|"
                    + "cuda|"
                    + "pytorch|"
                    + "tensorflow"
                    + ")\\b"
    );

    private NanobaseAiBrandSanitizer() {
    }

    public static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return PRODUCT_DISPLAY_NAME + " is unavailable.";
        }
        String cleaned = VENDOR.matcher(message).replaceAll(PRODUCT_DISPLAY_NAME);
        cleaned = cleaned.replaceAll("(?i)openai-compatible", PRODUCT_DISPLAY_NAME);
        cleaned = cleaned.replaceAll("(?i)open\\s*ai", PRODUCT_DISPLAY_NAME);
        if (cleaned.toLowerCase(Locale.ROOT).contains("mock")) {
            cleaned = cleaned.replaceAll("(?i)\\bmock\\b", "offline");
        }
        return cleaned;
    }

    public static String displayModelName(String raw) {
        // Never surface underlying model / deployment keys to the portal.
        Objects.requireNonNullElse(raw, "");
        return PRODUCT_DISPLAY_NAME;
    }

    public static String publicMode(String internalKind) {
        if (internalKind == null || internalKind.isBlank()) {
            return "offline";
        }
        String kind = internalKind.trim().toLowerCase(Locale.ROOT);
        if ("mock".equals(kind)) {
            return "offline";
        }
        return "nanobaseai";
    }

    public static String requireNonVendor(String message) {
        return Objects.requireNonNullElse(sanitize(message), PRODUCT_DISPLAY_NAME + " is unavailable.");
    }

    public static String draftStatusLabel() {
        return "Taslak (" + PRODUCT_DISPLAY_NAME + ")";
    }
}
