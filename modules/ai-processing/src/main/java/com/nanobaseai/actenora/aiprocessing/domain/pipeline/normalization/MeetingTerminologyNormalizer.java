package com.nanobaseai.actenora.aiprocessing.domain.pipeline.normalization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic product/company ASR fixes for the AI path (FAZ-9 compatible surface rewrite).
 * Built-in Nanobase defaults; additional aliases can be supplied at construction.
 */
public final class MeetingTerminologyNormalizer {

    public record Alias(String surface, String canonical) {
    }

    private final List<Alias> aliases;

    public MeetingTerminologyNormalizer(List<Alias> aliases) {
        List<Alias> copy = new ArrayList<>(Objects.requireNonNull(aliases, "aliases"));
        copy.sort(Comparator
                .comparingInt((Alias a) -> a.surface().length())
                .reversed()
                .thenComparing(a -> a.surface().toLowerCase(Locale.ROOT)));
        this.aliases = List.copyOf(copy);
    }

    public static MeetingTerminologyNormalizer productionDefaults() {
        return new MeetingTerminologyNormalizer(List.of(
                new Alias("Mayusque", "MySQL"),
                new Alias("Moyus gale", "MySQL"),
                new Alias("moyusque", "MySQL"),
                new Alias("Moyusque", "MySQL"),
                new Alias("moyus gale", "MySQL"),
                new Alias("poscree", "PostgreSQL"),
                new Alias("Poscree", "PostgreSQL"),
                new Alias("poscre", "PostgreSQL"),
                new Alias("Postgre", "PostgreSQL"),
                new Alias("g p u", "GPU"),
                new Alias("gpu", "GPU"),
                new Alias("n vidia", "NVIDIA"),
                new Alias("envidia", "NVIDIA"),
                new Alias("nivida", "NVIDIA"),
                new Alias("nvidia", "NVIDIA"),
                new Alias("d w h", "DWH"),
                new Alias("dwh", "DWH"),
                new Alias("p o c", "PoC"),
                new Alias("proof of concept", "PoC"),
                new Alias("poc", "PoC"),
                new Alias("s i e m", "SIEM"),
                new Alias("si em", "SIEM"),
                new Alias("siem", "SIEM"),
                new Alias("kor banking", "Core Banking"),
                new Alias("core bankin", "Core Banking"),
                new Alias("core bankacılık", "Core Banking"),
                new Alias("core banking", "Core Banking")
        ));
    }

    public String rewrite(String text) {
        if (text == null || text.isEmpty() || aliases.isEmpty()) {
            return text == null ? "" : text;
        }
        String result = text;
        for (Alias alias : aliases) {
            Pattern pattern = Pattern.compile(
                    "(?<![\\p{L}\\p{N}_])" + Pattern.quote(alias.surface()) + "(?![\\p{L}\\p{N}_])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher matcher = pattern.matcher(result);
            StringBuffer sb = new StringBuffer();
            boolean changed = false;
            while (matcher.find()) {
                if (!matcher.group().equals(alias.canonical())) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(alias.canonical()));
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
        return result;
    }
}
