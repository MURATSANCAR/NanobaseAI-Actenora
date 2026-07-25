package com.nanobaseai.actenora.transcript.domain.dictionary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies tenant dictionary exact/alias replacements deterministically.
 * Longer surface forms win to avoid partial alias collisions.
 */
public final class DictionaryMatcher {

    private DictionaryMatcher() {
    }

    public static RewriteResult rewrite(String text, TenantDictionary dictionary) {
        if (text == null || text.isEmpty()) {
            return new RewriteResult(text == null ? "" : text, 0);
        }

        List<DictionaryEntry> termEntries = new ArrayList<>();
        termEntries.addAll(dictionary.activeOfKind(DictionaryEntryKind.PRODUCT));
        termEntries.addAll(dictionary.activeOfKind(DictionaryEntryKind.COMPANY));
        termEntries.addAll(dictionary.activeOfKind(DictionaryEntryKind.PROJECT));

        List<Surface> surfaces = new ArrayList<>();
        for (DictionaryEntry entry : termEntries) {
            for (String form : entry.allSurfaceForms()) {
                if (form == null || form.isBlank()) {
                    continue;
                }
                surfaces.add(new Surface(form, entry.canonical()));
            }
        }
        surfaces.sort(Comparator
                .comparingInt((Surface s) -> s.form.length())
                .reversed()
                .thenComparing(s -> s.form)
                .thenComparing(s -> s.canonical));

        String result = text;
        int rewrites = 0;
        for (Surface surface : surfaces) {
            Pattern pattern = Pattern.compile(
                    "(?<![\\p{L}\\p{N}_])" + Pattern.quote(surface.form) + "(?![\\p{L}\\p{N}_])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher matcher = pattern.matcher(result);
            StringBuffer sb = new StringBuffer();
            boolean changed = false;
            while (matcher.find()) {
                if (!matcher.group().equals(surface.canonical)) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(surface.canonical));
                    rewrites++;
                    changed = true;
                } else {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
                }
            }
            matcher.appendTail(sb);
            if (changed) {
                result = sb.toString();
            }
        }
        return new RewriteResult(result, rewrites);
    }

    public record RewriteResult(String text, int rewriteCount) {
    }

    private record Surface(String form, String canonical) {
    }
}
