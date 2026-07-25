package com.nanobaseai.actenora.audit.domain;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Ensures audit metadata never retains transcript bodies, private notes, or raw prompts. */
public final class AuditContentGuard {

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "transcript", "transcript_text", "transcripttext",
            "private_note", "privatenote", "private_notes",
            "raw_prompt", "rawprompt", "prompt", "prompt_text", "llm_prompt", "llmprompt"
    );

    private AuditContentGuard() {}

    public static Map<String, Object> sanitize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (isForbidden(entry.getKey())) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                result.put(entry.getKey(), sanitize(nestedMap));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    public static void assertAllowed(Map<String, Object> metadata) {
        if (metadata == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (isForbidden(entry.getKey())) {
                throw new ForbiddenAuditContentException(entry.getKey());
            }
            if (entry.getValue() instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                assertAllowed(nestedMap);
            }
        }
    }

    public static boolean isForbidden(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        String normalized = lower.replace("-", "").replace("_", "");
        return FORBIDDEN_KEYS.contains(lower) || FORBIDDEN_KEYS.contains(normalized);
    }
}
