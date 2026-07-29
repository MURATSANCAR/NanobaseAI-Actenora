package com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Dual text views: original for speech-act / display; comparison core for semantic match.
 */
public final class ItemTextViews {

    private static final Pattern SCAFFOLD = Pattern.compile(
            "(?iu)(hen[uü]z\\s+karar\\s+de[gğ]il\\s*[,;:]?\\s*)"
                    + "|(de[gğ]erlendirdi[gğ]imiz\\s+se[cç]enek\\s+[sş]u\\s*[,;:]?\\s*)"
                    + "|(bu\\s+öneriyi\\s+not\\s+ediyorum(\\s+ama)?\\s*)"
    );

    private ItemTextViews() {
    }

    public static String comparisonCore(String originalText) {
        if (originalText == null || originalText.isBlank()) {
            return "";
        }
        String stripped = SCAFFOLD.matcher(originalText.strip()).replaceAll(" ");
        // Preserve encoding / compound tokens before punctuation wipe (UTF-8 → utf 8 breaks matchers).
        String normalized = stripped.toLowerCase(Locale.ROOT)
                .replace("utf-8", "utf8")
                .replace("quoted-printable", "quotedprintable");
        return normalized
                .replaceAll("[\\p{Punct}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static String originalOrEmpty(String originalText) {
        return originalText == null ? "" : originalText.strip();
    }
}
