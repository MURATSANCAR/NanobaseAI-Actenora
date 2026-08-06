package com.nanobaseai.actenora.aiprocessing.domain.pipeline.normalization;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic product/company ASR fixes for the AI path (FAZ-9 compatible surface rewrite).
 * Built-in Nanobase defaults; additional aliases can be supplied at construction.
 */
public final class MeetingTerminologyNormalizer {

    public record Alias(String surface, String canonical) {
    }

    private final List<Alias> aliases;

    /** O(text) matcher — scales to hundred-thousand terms (single-pass token scan). */
    private final GlossaryMatcher matcher;

    public MeetingTerminologyNormalizer(List<Alias> aliases) {
        List<Alias> copy = new ArrayList<>(Objects.requireNonNull(aliases, "aliases"));
        this.aliases = List.copyOf(copy);
        Map<String, String> surfaceToCanonical = new LinkedHashMap<>();
        for (Alias al : copy) {
            if (al != null && al.surface() != null && al.canonical() != null) {
                surfaceToCanonical.putIfAbsent(al.surface(), al.canonical());
            }
        }
        this.matcher = new GlossaryMatcher(surfaceToCanonical);
    }

    /**
     * Multi-sector deterministic ASR/term glossary (finance, IT/AI, insurance, telecom, general).
     * Only high-confidence surface forms — uncommon misspellings, spelled-out acronyms and
     * casing fixes — so no common Turkish word is ever rewritten. Grow per sector as needed.
     */
    public static MeetingTerminologyNormalizer productionDefaults() {
        return new MeetingTerminologyNormalizer(loadFromResource("/aiprocessing/glossary/base-glossary.csv"));
    }

    /** Data-driven glossary: load "surface,canonical" pairs from a classpath CSV resource. */
    static List<Alias> loadFromResource(String resourcePath) {
        List<Alias> out = new ArrayList<>();
        try (InputStream in = MeetingTerminologyNormalizer.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return fallbackDefaults();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.strip();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int comma = trimmed.indexOf(',');
                    if (comma <= 0 || comma == trimmed.length() - 1) {
                        continue;
                    }
                    String surface = trimmed.substring(0, comma).strip();
                    String canonical = trimmed.substring(comma + 1).strip();
                    if (!surface.isEmpty() && !canonical.isEmpty()) {
                        out.add(new Alias(surface, canonical));
                    }
                }
            }
        } catch (IOException ex) {
            return fallbackDefaults();
        }
        return out.isEmpty() ? fallbackDefaults() : out;
    }

    /** Minimal safety net if the resource is unavailable. */
    private static List<Alias> fallbackDefaults() {
        return List.of(
                new Alias("Fimple", "Simple"),
                new Alias("invdiya", "NVIDIA"),
                new Alias("corbanking", "Core Banking"),
                new Alias("on prem", "on-prem")
        );
    }

    public String rewrite(String text) {
        return matcher.rewrite(text);
    }
}
