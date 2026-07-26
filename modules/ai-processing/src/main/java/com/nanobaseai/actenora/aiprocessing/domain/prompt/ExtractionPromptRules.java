package com.nanobaseai.actenora.aiprocessing.domain.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Hard rules embedded with every extraction / synthesis / audit prompt.
 * Output language follows the job/user language — never mix languages in user-facing fields.
 */
public final class ExtractionPromptRules {

    private static final String BASE_RULES = loadSystemRules();
    private static final Map<String, String> LANGUAGE_NAMES = Map.of(
            "tr", "Türkçe",
            "en", "English",
            "de", "Deutsch",
            "fr", "Français",
            "es", "Español"
    );

    /** Default rules for Turkish jobs (backward compatible). */
    public static final String SYSTEM_RULES = systemRulesFor("tr");

    private ExtractionPromptRules() {
    }

    public static String systemRulesFor(String language) {
        String code = normalizeLanguage(language);
        String rules = applyLanguage(BASE_RULES, code);
        if ("en".equals(code)) {
            return rules + """

                    CRITICAL: Every user-facing narrative field MUST be English only.
                    Never emit Turkish (or any other language) in purpose, executiveSummary, topics, decisions, actions, risks, commitments, or openQuestions.
                    Do not write English system meta such as "Meeting extraction completed…" unless the output language is English and the content is empty — then use a short English empty-state sentence.
                    """;
        }
        if ("tr".equals(code)) {
            return rules + """

                    KRİTİK: Tüm kullanıcıya görünen metin alanları yalnızca Türkçe olmalı.
                    purpose, executiveSummary, topics, decisions, actionItems, risks, commitments, openQuestions içinde İngilizce (veya başka dil) cümle yazma.
                    Özellikle "Meeting extraction completed with no primary topics." gibi İngilizce meta/özet cümleleri yasaktır; boşsa Türkçe boş-özet yaz.
                    """;
        }
        return rules + "\n\nCRITICAL: Write every user-facing narrative field only in "
                + LANGUAGE_NAMES.getOrDefault(code, code)
                + " (" + code + "). Never mix languages.";
    }

    public static String applyLanguage(String template, String language) {
        String code = normalizeLanguage(language);
        String name = LANGUAGE_NAMES.getOrDefault(code, code);
        return template
                .replace("{{outputLanguage}}", name)
                .replace("{{outputLanguageCode}}", code);
    }

    public static String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "tr";
        }
        String trimmed = language.trim().toLowerCase(Locale.ROOT);
        int dash = trimmed.indexOf('-');
        if (dash > 0) {
            trimmed = trimmed.substring(0, dash);
        }
        return trimmed.isBlank() ? "tr" : trimmed;
    }

    private static String loadSystemRules() {
        try (InputStream in = ExtractionPromptRules.class.getResourceAsStream(
                "/aiprocessing/prompts/system-meeting-analyst.v1.txt")) {
            if (in == null) {
                return """
                        You extract structured meeting facts from the meeting conversation only.
                        Never invent facts. Respond with JSON only.
                        Write every user-facing text field only in {{outputLanguage}} ({{outputLanguageCode}}).
                        """;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load system prompt", ex);
        }
    }
}
