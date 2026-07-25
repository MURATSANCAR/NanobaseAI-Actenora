package com.nanobaseai.actenora.observability;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * PII-safe / secret-safe field redaction for structured logs (FAZ 25).
 * Never allow raw transcript text, bearer tokens, API keys, or passwords into logs.
 */
public final class PiiRedactor {

    public static final String REDACTED = "[REDACTED]";

    private static final Set<String> BLOCKED_KEYS = Set.of(
            "password",
            "passwd",
            "secret",
            "token",
            "accesstoken",
            "refreshtoken",
            "apikey",
            "api_key",
            "authorization",
            "auth",
            "bearer",
            "clientsecret",
            "client_secret",
            "transcript",
            "transcripttext",
            "transcript_text",
            "rawtranscript",
            "raw_transcript",
            "prompt",
            "completion",
            "responsebody",
            "email",
            "ssn",
            "creditcard",
            "credit_card"
    );

    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[a-z0-9._\\-]+");
    private static final Pattern API_KEY = Pattern.compile("(?i)(api[_-]?key\\s*[:=]\\s*)[^\\s\"']+");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b");
    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    private PiiRedactor() {
    }

    public static boolean isBlockedKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        if (BLOCKED_KEYS.contains(normalized)) {
            return true;
        }
        return normalized.contains("transcript")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("apikey")
                || normalized.endsWith("token");
    }

    public static String redactValue(String key, String value) {
        if (value == null) {
            return null;
        }
        if (isBlockedKey(key)) {
            return REDACTED;
        }
        return redactSecretsInText(value);
    }

    public static String redactSecretsInText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String redacted = BEARER.matcher(text).replaceAll("$1" + REDACTED);
        redacted = API_KEY.matcher(redacted).replaceAll("$1" + REDACTED);
        redacted = JWT.matcher(redacted).replaceAll(REDACTED);
        redacted = EMAIL.matcher(redacted).replaceAll(REDACTED);
        return redacted;
    }

    public static Map<String, String> redactMap(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            return Map.of();
        }
        Map<String, String> safe = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            safe.put(entry.getKey(), redactValue(entry.getKey(), entry.getValue()));
        }
        return Map.copyOf(safe);
    }

    /**
     * Returns true when the rendered log line still contains raw transcript-like content
     * that should never appear (used by tests and log guards).
     */
    public static boolean containsTranscriptLeak(String logLine, String transcriptSnippet) {
        if (logLine == null || transcriptSnippet == null || transcriptSnippet.isBlank()) {
            return false;
        }
        return logLine.contains(transcriptSnippet);
    }
}
