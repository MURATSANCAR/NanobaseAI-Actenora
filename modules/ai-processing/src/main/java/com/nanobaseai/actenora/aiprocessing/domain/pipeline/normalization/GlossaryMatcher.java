package com.nanobaseai.actenora.aiprocessing.domain.pipeline.normalization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * O(text) word-boundary term rewriter for large glossaries (thousands–hundred-thousands).
 *
 * <p>Unlike a per-alias regex scan (O(terms × text)), this tokenizes the text once and does
 * O(1) hash lookups over sliding n-gram windows, so scan cost is independent of glossary size.
 * Matching is whole-word (Turkish suffixes like {@code API'ler} are preserved; {@code api} never
 * touches {@code apiler}), longest-phrase-wins, and case-insensitive via ROOT-locale folding.
 * Original spacing/punctuation outside matched spans is preserved.
 */
public final class GlossaryMatcher {

    private static final Pattern WORD =
            Pattern.compile("[\\p{L}\\p{N}]+", Pattern.UNICODE_CHARACTER_CLASS);

    private final Map<String, String> byKey;
    private final int maxWords;

    public GlossaryMatcher(Map<String, String> surfaceToCanonical) {
        Objects.requireNonNull(surfaceToCanonical, "surfaceToCanonical");
        Map<String, String> normalized = new HashMap<>(surfaceToCanonical.size() * 2);
        int max = 1;
        for (Map.Entry<String, String> e : surfaceToCanonical.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String key = normalizeKey(e.getKey());
            if (key.isEmpty()) {
                continue;
            }
            normalized.putIfAbsent(key, e.getValue());
            int words = key.split(" ").length;
            if (words > max) {
                max = words;
            }
        }
        this.byKey = Map.copyOf(normalized);
        this.maxWords = max;
    }

    public boolean isEmpty() {
        return byKey.isEmpty();
    }

    private static String normalizeKey(String s) {
        return s.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public String rewrite(String text) {
        if (text == null || text.isEmpty() || byKey.isEmpty()) {
            return text == null ? "" : text;
        }
        List<int[]> tokens = new ArrayList<>();
        Matcher wm = WORD.matcher(text);
        while (wm.find()) {
            tokens.add(new int[] {wm.start(), wm.end()});
        }
        if (tokens.isEmpty()) {
            return text;
        }

        List<int[]> spans = new ArrayList<>();
        List<String> replacements = new ArrayList<>();
        int i = 0;
        while (i < tokens.size()) {
            int maxK = Math.min(maxWords, tokens.size() - i);
            int matchedLen = 0;
            String canonical = null;
            for (int k = maxK; k >= 1; k--) {
                StringBuilder kb = new StringBuilder();
                for (int j = 0; j < k; j++) {
                    if (j > 0) {
                        kb.append(' ');
                    }
                    int[] t = tokens.get(i + j);
                    kb.append(text, t[0], t[1]);
                }
                String c = byKey.get(normalizeKey(kb.toString()));
                if (c != null) {
                    matchedLen = k;
                    canonical = c;
                    break;
                }
            }
            if (canonical != null) {
                int start = tokens.get(i)[0];
                int end = tokens.get(i + matchedLen - 1)[1];
                if (!text.substring(start, end).equals(canonical)) {
                    spans.add(new int[] {start, end});
                    replacements.add(canonical);
                }
                i += matchedLen;
            } else {
                i++;
            }
        }
        if (spans.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text);
        for (int s = spans.size() - 1; s >= 0; s--) {
            sb.replace(spans.get(s)[0], spans.get(s)[1], replacements.get(s));
        }
        return sb.toString();
    }
}
