package com.nanobaseai.actenora.aiprocessing.domain.pipeline.normalization;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strips high-precision in-content self-attribution prefixes so noise filters see the
 * real utterance rather than {@code "Can olarak ekliyorum: …"} wrappers.
 *
 * <p>Does not depend on a speaker-name allowlist — only the attribution speech form.
 */
public final class InContentAttributionStripper {

    private static final Pattern SELF_ATTRIBUTION = Pattern.compile(
            "(?iu)^\\s*(\\p{L}+(?:[\\s'-]+\\p{L}+){0,3})\\s+olarak\\s+"
                    + "(ekliyorum|not\\s+ediyorum|söylüyorum|paylaşıyorum)\\s*:?\\s*"
    );

    public String strip(String content) {
        if (content == null || content.isBlank()) {
            return content == null ? "" : content;
        }
        Matcher matcher = SELF_ATTRIBUTION.matcher(content);
        if (!matcher.find()) {
            return content;
        }
        String remainder = content.substring(matcher.end()).strip();
        return remainder.isEmpty() ? content.strip() : remainder;
    }
}
