package com.nanobaseai.actenora.aiprocessing.domain.pipeline.normalization;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colloquial meeting register → operational Turkish used in corporate minutes.
 */
public final class DomainRegisterNormalizer {

    private static final Pattern RECIPE = Pattern.compile(
            "(?iu)\\bre[cç]ete(lerini|lerimizi|lerin|leri|ler|sini|sını|sine|sına|sinde|sında|sinden|sından|si|sı|yi|yı|ye|ya|de|da|den|dan)?\\b"
    );

    public String rewrite(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        Matcher matcher = RECIPE.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String matched = matcher.group();
            String replacement = replacementFor(matched);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String replacementFor(String matched) {
        String lower = matched.toLowerCase(Locale.forLanguageTag("tr"));
        if (lower.startsWith("reçete") || lower.startsWith("recete")) {
            if (lower.contains("lerini") || lower.contains("lerimizi")) {
                return "gereksinim dokümanlarını";
            }
            if (lower.endsWith("leri") || lower.endsWith("ler") || lower.endsWith("lerin")) {
                return "gereksinim dokümanları";
            }
            if (lower.endsWith("sini") || lower.endsWith("sını") || lower.endsWith("yi") || lower.endsWith("yı")) {
                return "gereksinim dokümanını";
            }
            if (lower.endsWith("sine") || lower.endsWith("sına") || lower.endsWith("ye") || lower.endsWith("ya")) {
                return "gereksinim dokümanına";
            }
            if (lower.endsWith("sinde") || lower.endsWith("sında") || lower.endsWith("de") || lower.endsWith("da")) {
                return "gereksinim dokümanında";
            }
            if (lower.endsWith("sinden") || lower.endsWith("sından") || lower.endsWith("den") || lower.endsWith("dan")) {
                return "gereksinim dokümanından";
            }
            if (lower.endsWith("si") || lower.endsWith("sı")) {
                return "gereksinim dokümanı";
            }
            return "gereksinim dokümanı";
        }
        return "gereksinim dokümanı";
    }
}
