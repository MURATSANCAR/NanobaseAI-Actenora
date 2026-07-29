package com.nanobaseai.actenora.aiprocessing.domain.pipeline.action;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strips discourse labels from the start of action display text only.
 * Does not mutate evidence / source segment content.
 */
public final class ActionDiscoursePrefixNormalizer {

    private static final Pattern PREFIX = Pattern.compile(
            "(?iu)^\\s*(?:"
                    + "aksiyon\\s+kayd[ıi]"
                    + "|aksiyon\\s+maddesi"
                    + "|aksiyon"
                    + "|g[oö]rev\\s+kayd[ıi]"
                    + "|g[oö]rev"
                    + "|[sş]unu\\s+aksiyon\\s+olarak\\s+kaydedelim"
                    + ")\\s*[:\\-–—]\\s*"
    );

    public String strip(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        Matcher matcher = PREFIX.matcher(text);
        if (!matcher.find()) {
            return text.strip();
        }
        String remainder = text.substring(matcher.end()).strip();
        return remainder.isEmpty() ? text.strip() : remainder;
    }

    public boolean startsWithDiscoursePrefix(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return PREFIX.matcher(text).lookingAt();
    }

    /** Lowercased comparison helper for audits. */
    public static String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
