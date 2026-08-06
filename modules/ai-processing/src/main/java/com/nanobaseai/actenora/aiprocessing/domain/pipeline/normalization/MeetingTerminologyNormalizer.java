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

    /**
     * Multi-sector deterministic ASR/term glossary (finance, IT/AI, insurance, telecom, general).
     * Only high-confidence surface forms — uncommon misspellings, spelled-out acronyms and
     * casing fixes — so no common Turkish word is ever rewritten. Grow per sector as needed.
     */
    public static MeetingTerminologyNormalizer productionDefaults() {
        List<Alias> a = new ArrayList<>();

        // ---- Veritabanı ----
        a.add(new Alias("Mayusque", "MySQL"));
        a.add(new Alias("Moyus gale", "MySQL"));
        a.add(new Alias("moyusque", "MySQL"));
        a.add(new Alias("Moyusque", "MySQL"));
        a.add(new Alias("moyus gale", "MySQL"));
        a.add(new Alias("poscree", "PostgreSQL"));
        a.add(new Alias("Poscree", "PostgreSQL"));
        a.add(new Alias("poscre", "PostgreSQL"));
        a.add(new Alias("Postgre", "PostgreSQL"));
        a.add(new Alias("postgres", "PostgreSQL"));

        // ---- Donanım / AI altyapı ----
        a.add(new Alias("g p u", "GPU"));
        a.add(new Alias("gpu", "GPU"));
        a.add(new Alias("n vidia", "NVIDIA"));
        a.add(new Alias("in vidia", "NVIDIA"));
        a.add(new Alias("invdiya", "NVIDIA"));
        a.add(new Alias("invidya", "NVIDIA"));
        a.add(new Alias("invdia", "NVIDIA"));
        a.add(new Alias("envidia", "NVIDIA"));
        a.add(new Alias("nivida", "NVIDIA"));
        a.add(new Alias("nvidia", "NVIDIA"));
        a.add(new Alias("c u d a", "CUDA"));
        a.add(new Alias("cuda", "CUDA"));
        a.add(new Alias("h 200", "H200"));
        a.add(new Alias("h200", "H200"));

        // ---- IT / Yazılım / AI ----
        a.add(new Alias("d w h", "DWH"));
        a.add(new Alias("dwh", "DWH"));
        a.add(new Alias("d vaş", "DWH"));
        a.add(new Alias("devaaş", "DWH"));
        a.add(new Alias("e t l", "ETL"));
        a.add(new Alias("l l m", "LLM"));
        a.add(new Alias("gpt", "GPT"));
        a.add(new Alias("çet gpt", "GPT"));
        a.add(new Alias("chat gpt", "GPT"));
        a.add(new Alias("çet cpt", "GPT"));
        a.add(new Alias("cet gpt", "GPT"));
        a.add(new Alias("kubernates", "Kubernetes"));
        a.add(new Alias("kubernetis", "Kubernetes"));
        a.add(new Alias("k8s", "Kubernetes"));
        a.add(new Alias("on prem", "on-prem"));
        a.add(new Alias("on premise", "on-prem"));
        a.add(new Alias("on premises", "on-prem"));
        a.add(new Alias("onprem", "on-prem"));
        a.add(new Alias("10 prem", "on-prem"));
        a.add(new Alias("p o c", "PoC"));
        a.add(new Alias("proof of concept", "PoC"));
        a.add(new Alias("poc", "PoC"));

        // ---- Güvenlik ----
        a.add(new Alias("s i e m", "SIEM"));
        a.add(new Alias("si em", "SIEM"));
        a.add(new Alias("siem", "SIEM"));
        a.add(new Alias("k y c", "KYC"));

        // ---- Finans / Bankacılık ----
        a.add(new Alias("kor banking", "Core Banking"));
        a.add(new Alias("core bankin", "Core Banking"));
        a.add(new Alias("core bankacılık", "Core Banking"));
        a.add(new Alias("core banking", "Core Banking"));
        a.add(new Alias("corbanking", "Core Banking"));
        a.add(new Alias("corben king", "Core Banking"));
        a.add(new Alias("corbenk", "Core Banking"));
        a.add(new Alias("cor banking", "Core Banking"));
        a.add(new Alias("Fimple", "Simple"));
        a.add(new Alias("fimple", "Simple"));
        a.add(new Alias("Simpıl", "Simple"));
        a.add(new Alias("findeks", "Findeks"));
        a.add(new Alias("findex", "Findeks"));
        a.add(new Alias("b d d k", "BDDK"));
        a.add(new Alias("k v k k", "KVKK"));

        // ---- Telekom ----
        a.add(new Alias("g s m", "GSM"));

        return new MeetingTerminologyNormalizer(a);
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
