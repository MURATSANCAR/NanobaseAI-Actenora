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
        String candidate = applyRepair(raw.trim());
        for (int pass = 0; pass < MAX_REPAIR_PASSES; pass++) {
            if (looksLikeJsonObject(candidate) && !hasTrailingComma(candidate)) {
                return candidate;
            }
            candidate = applyRepair(candidate);
        }
        if (looksLikeJsonObject(candidate) && !hasTrailingComma(candidate)) {
            return candidate;
        }
        throw new PipelineException(
                FailureCategory.INVALID_JSON,
                PipelineStage.EXTRACT,
                "Unable to repair model JSON"
        );
    }

    public boolean needsRepair(String raw) {
        if (raw == null) {
            return true;
        }
        String trimmed = raw.trim();
        return !looksLikeJsonObject(trimmed) || hasTrailingComma(trimmed);
    }

    private static boolean hasTrailingComma(String text) {
        // Detect ,} or ,] outside of strings.
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
                continue;
            }
            if (c == ',') {
                int j = i + 1;
                while (j < text.length() && Character.isWhitespace(text.charAt(j))) {
                    j++;
                }
                if (j < text.length()) {
                    char next = text.charAt(j);
                    if (next == '}' || next == ']') {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String applyRepair(String input) {
        String text = input.trim();
        if (text.startsWith("```")) {
            text = stripFence(text);
        }
        int start = text.indexOf('{');
        if (start >= 0) {
            text = text.substring(start);
        }
        text = text.replaceAll(",\\s*}", "}");
        text = text.replaceAll(",\\s*]", "]");
        text = closeUnterminated(text);
        return text.trim();
    }

    /** Best-effort close for truncated model JSON (unclosed strings / arrays / objects). */
    private static String closeUnterminated(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text);
        boolean inString = false;
        boolean escape = false;
        // Track openers in order so we close LIFO (} before ] when object is inside array).
        StringBuilder openers = new StringBuilder();
        for (int i = 0; i < out.length(); i++) {
            char c = out.charAt(i);
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
                openers.append('{');
            } else if (c == '}') {
                if (!openers.isEmpty() && openers.charAt(openers.length() - 1) == '{') {
                    openers.deleteCharAt(openers.length() - 1);
                }
            } else if (c == '[') {
                openers.append('[');
            } else if (c == ']') {
                if (!openers.isEmpty() && openers.charAt(openers.length() - 1) == '[') {
                    openers.deleteCharAt(openers.length() - 1);
                }
            }
        }
        if (inString) {
            out.append('"');
        }
        stripDanglingPartialMember(out);
        for (int i = openers.length() - 1; i >= 0; i--) {
            char opener = openers.charAt(i);
            out.append(opener == '{' ? '}' : ']');
        }
        return out.toString();
    }

    /**
     * Truncation often leaves {@code "key":} or a trailing comma with no value.
     * Closing braces then yields {@code "key": }} which Jackson rejects.
     */
    private static void stripDanglingPartialMember(StringBuilder out) {
        while (true) {
            int end = out.length() - 1;
            while (end >= 0 && Character.isWhitespace(out.charAt(end))) {
                end--;
            }
            if (end < 0) {
                return;
            }
            char last = out.charAt(end);
            if (last == ',') {
                out.delete(end, out.length());
                continue;
            }
            if (last != ':') {
                if (end + 1 < out.length()) {
                    out.delete(end + 1, out.length());
                }
                return;
            }
            // Remove back through {@code "key":} (or bare key:) so the parent can close cleanly.
            int cut = end - 1;
            while (cut >= 0 && Character.isWhitespace(out.charAt(cut))) {
                cut--;
            }
            int keyStart;
            if (cut >= 0 && out.charAt(cut) == '"') {
                keyStart = cut;
                cut--;
                while (cut >= 0) {
                    char c = out.charAt(cut);
                    if (c == '"' && (cut == 0 || out.charAt(cut - 1) != '\\')) {
                        keyStart = cut;
                        break;
                    }
                    cut--;
                }
            } else {
                while (cut >= 0 && (Character.isLetterOrDigit(out.charAt(cut)) || out.charAt(cut) == '_')) {
                    cut--;
                }
                keyStart = cut + 1;
            }
            int beforeKey = keyStart - 1;
            while (beforeKey >= 0 && Character.isWhitespace(out.charAt(beforeKey))) {
                beforeKey--;
            }
            if (beforeKey >= 0 && out.charAt(beforeKey) == ',') {
                out.delete(beforeKey, out.length());
                continue;
            }
            out.delete(keyStart, out.length());
            return;
        }
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
        int brace = 0;
        int bracket = 0;
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
                brace++;
            } else if (c == '}') {
                brace--;
                if (brace < 0) {
                    return false;
                }
            } else if (c == '[') {
                bracket++;
            } else if (c == ']') {
                bracket--;
                if (bracket < 0) {
                    return false;
                }
            }
        }
        return brace == 0 && bracket == 0 && !inString;
    }
}
