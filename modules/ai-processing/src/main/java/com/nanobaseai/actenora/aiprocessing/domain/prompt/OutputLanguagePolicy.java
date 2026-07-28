package com.nanobaseai.actenora.aiprocessing.domain.prompt;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Output-language policy for AI note fields: normalize codes, localize empty summaries,
 * and replace known wrong-language meta sentences the model sometimes emits.
 */
public final class OutputLanguagePolicy {

    private static final Pattern ENGLISH_EMPTY_META = Pattern.compile(
            "(?i).*\\b(meeting\\s+extraction\\s+completed|no\\s+primary\\s+topics|"
                    + "no\\s+primary\\s+topic|manual\\s+review\\s+recommended|"
                    + "extraction\\s+completed)\\b.*"
    );

    private static final Pattern TURKISH_EMPTY_META = Pattern.compile(
            "(?i).*\\b(çıkarım\\s+tamamlandı|birincil\\s+konu|manuel\\s+inceleme)\\b.*"
    );

    private OutputLanguagePolicy() {
    }

    public static String normalize(String language) {
        return ExtractionPromptRules.normalizeLanguage(language);
    }

    public static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return normalize(primary);
        }
        if (fallback != null && !fallback.isBlank()) {
            return normalize(fallback);
        }
        return "tr";
    }

    public static String emptySummary(String language) {
        return "en".equals(normalize(language))
                ? "Extraction completed; no primary topics or decisions found. Manual review recommended."
                : "Çıkarım tamamlandı; birincil konu/karar bulunamadı. Manuel inceleme önerilir.";
    }

    public static String unreliableSummary(String language) {
        return "en".equals(normalize(language))
                ? "A reliable executive summary could not be produced from this meeting."
                : "Toplantıdan güvenilir bir yönetici özeti üretilemedi.";
    }

    /**
     * If the model returns a known empty/meta sentence in the wrong language, replace it.
     */
    public static String sanitizeUserFacingText(String text, String language) {
        if (text == null || text.isBlank()) {
            return emptySummary(language);
        }
        String lang = normalize(language);
        String trimmed = text.trim();
        if ("tr".equals(lang) && ENGLISH_EMPTY_META.matcher(trimmed).matches()) {
            return emptySummary("tr");
        }
        if ("en".equals(lang) && TURKISH_EMPTY_META.matcher(trimmed).matches()) {
            return emptySummary("en");
        }
        return trimmed;
    }
}
