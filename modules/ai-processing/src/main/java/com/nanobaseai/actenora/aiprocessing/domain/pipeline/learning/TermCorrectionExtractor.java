package com.nanobaseai.actenora.aiprocessing.domain.pipeline.learning;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Katman 1 learning core: given an AI-generated text and its user-edited version, detect a single
 * localized term substitution (e.g. "Fimple" → "Simple", "invdiya" → "NVIDIA") that is safe to feed
 * into the tenant glossary. Conservative by design: only clear, short, whole-word swaps with
 * identical surrounding context are learned; multi-region rewrites and common-word edits are ignored.
 */
public final class TermCorrectionExtractor {

    public record Correction(String surface, String canonical) {
    }

    private static final Pattern WORD =
            Pattern.compile("[\\p{L}\\p{N}]+", Pattern.UNICODE_CHARACTER_CLASS);

    /** Max words on either side of a learnable substitution. */
    private static final int MAX_SPAN_WORDS = 3;

    /** Very common tokens that must never be learned as a glossary term. */
    private static final Set<String> STOPWORDS = Set.of(
            "bir", "ve", "ile", "için", "bu", "şu", "de", "da", "ki", "the", "a", "an",
            "of", "to", "and", "or", "olarak", "gibi", "çok", "az", "var", "yok");

    public List<Correction> extract(String before, String after) {
        if (before == null || after == null) {
            return List.of();
        }
        List<String> b = tokenize(before);
        List<String> a = tokenize(after);
        if (b.isEmpty() || a.isEmpty()) {
            return List.of();
        }

        int prefix = 0;
        while (prefix < b.size() && prefix < a.size() && eq(b.get(prefix), a.get(prefix))) {
            prefix++;
        }
        int bEnd = b.size();
        int aEnd = a.size();
        while (bEnd > prefix && aEnd > prefix && eq(b.get(bEnd - 1), a.get(aEnd - 1))) {
            bEnd--;
            aEnd--;
        }

        List<String> beforeMid = b.subList(prefix, bEnd);
        List<String> afterMid = a.subList(prefix, aEnd);

        // Require a real, localized substitution on both sides.
        if (beforeMid.isEmpty() || afterMid.isEmpty()) {
            return List.of();
        }
        if (beforeMid.size() > MAX_SPAN_WORDS || afterMid.size() > MAX_SPAN_WORDS) {
            return List.of();
        }
        // Require shared context so we are confident this is a term swap, not a fresh sentence.
        if (prefix == 0 && bEnd == b.size() && aEnd == a.size()
                && b.size() > MAX_SPAN_WORDS) {
            return List.of();
        }

        String surface = String.join(" ", beforeMid).strip();
        String canonical = String.join(" ", afterMid).strip();
        if (surface.isEmpty() || canonical.isEmpty() || surface.equalsIgnoreCase(canonical)) {
            return List.of();
        }
        if (containsStopword(beforeMid) || containsStopword(afterMid)) {
            return List.of();
        }
        return List.of(new Correction(surface, canonical));
    }

    private static boolean containsStopword(List<String> tokens) {
        for (String t : tokens) {
            if (STOPWORDS.contains(t.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean eq(String x, String y) {
        return x.toLowerCase(Locale.ROOT).equals(y.toLowerCase(Locale.ROOT));
    }

    private static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = WORD.matcher(text);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }
}
