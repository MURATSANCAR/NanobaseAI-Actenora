package com.nanobaseai.actenora.security.aiprocessing;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Strips third-party technology vendor tokens from user-facing text.
 * Public product surface is NanobaseAI only.
 */
public final class NanobaseAiBrandSanitizer {

    private static final Pattern VENDOR = Pattern.compile(
            "(?i)\\b("
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
            return "NanobaseAI intelligence is unavailable.";
        }
        String cleaned = VENDOR.matcher(message).replaceAll("NanobaseAI");
        cleaned = cleaned.replaceAll("(?i)openai-compatible", "NanobaseAI");
        cleaned = cleaned.replaceAll("(?i)open\\s*ai", "NanobaseAI");
        if (cleaned.toLowerCase(Locale.ROOT).contains("mock")) {
            cleaned = cleaned.replaceAll("(?i)\\bmock\\b", "offline");
        }
        return cleaned;
    }

    public static String displayModelName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "NanobaseAI";
        }
        String sanitized = sanitize(raw);
        if (sanitized.toLowerCase(Locale.ROOT).contains("nanobaseai")) {
            return sanitized;
        }
        return "NanobaseAI · " + sanitized;
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
        return Objects.requireNonNullElse(sanitize(message), "NanobaseAI intelligence is unavailable.");
    }
}
