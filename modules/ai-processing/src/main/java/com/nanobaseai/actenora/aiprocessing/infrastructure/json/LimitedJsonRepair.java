package com.nanobaseai.actenora.aiprocessing.infrastructure.json;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FailureCategory;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.PipelineException;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.PipelineStage;

/**
 * Bounded syntactic repair only — never invents semantic fields.
 */
public final class LimitedJsonRepair {

    private static final int MAX_REPAIR_PASSES = 2;

    public String repairOrThrow(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new PipelineException(
                    FailureCategory.INVALID_JSON,
                    PipelineStage.EXTRACT,
                    "Empty model output"
            );
        }
        String candidate = raw.trim();
        for (int pass = 0; pass <= MAX_REPAIR_PASSES; pass++) {
            if (looksLikeJsonObject(candidate)) {
                return candidate;
            }
            candidate = applyRepair(candidate);
        }
        throw new PipelineException(
                FailureCategory.INVALID_JSON,
                PipelineStage.EXTRACT,
                "Unable to repair model JSON"
        );
    }

    public boolean needsRepair(String raw) {
        return raw == null || !looksLikeJsonObject(raw.trim());
    }

    private String applyRepair(String input) {
        String text = input.trim();
        if (text.startsWith("```")) {
            text = stripFence(text);
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        text = text.replaceAll(",\\s*}", "}");
        text = text.replaceAll(",\\s*]", "]");
        return text.trim();
    }

    private static String stripFence(String text) {
        String withoutOpen = text.replaceFirst("^```(?:json)?\\s*", "");
        int close = withoutOpen.lastIndexOf("```");
        if (close >= 0) {
            return withoutOpen.substring(0, close).trim();
        }
        return withoutOpen.trim();
    }

    private static boolean looksLikeJsonObject(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (!(text.startsWith("{") && text.endsWith("}"))) {
            return false;
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth < 0) {
                    return false;
                }
            }
        }
        return depth == 0 && !inString;
    }
}
