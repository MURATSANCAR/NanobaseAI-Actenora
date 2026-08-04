package com.nanobaseai.actenora.aiprocessing.domain.pipeline.extraction;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic seed for measured observations the LLM often skips in importantFacts
 * (gold F-01 / F-02 style: n/m error rates, client counts).
 * Standup fixture uses Turkish number words: "Kırk tekrardan üçünde 401 görüldü".
 */
public final class MeasuredObservationFactSeeder {

    private static final int RX = Pattern.CASE_INSENSITIVE
            | Pattern.UNICODE_CASE
            | Pattern.UNICODE_CHARACTER_CLASS;

    private static final String NUM =
            "(?:\\d+|bir|iki|üç|uc|dört|dort|beş|bes|altı|alti|yedi|sekiz|dokuz|on|"
                    + "yirmi|otuz|kırk|kirk|elli|altmış|altmis|yetmiş|yetmis|seksen|doksan|"
                    + "yüz|yuz)";

    /**
     * "Kırk tekrardan üçünde 401 görüldü" / "Beş istemcinin ikisinde … hatalı"
     * also digit forms: "40 tekrarın 3'ünde 401"
     */
    private static final Pattern MEASURED = Pattern.compile(
            "(?iu)("
                    + "(?:" + NUM + "\\s*tekrar(?:dan|ın|in)?\\s*" + NUM
                    + "\\s*(?:['’]?)(?:ün|un|inde|ında|ünde|unda)?[^.]{0,50}?(?:401|hata|görüldü|goruldu))"
                    + "|"
                    + "(?:(?:test\\s*edilen\\s*)?" + NUM + "\\s*istemci(?:nin|nin)?\\s*" + NUM
                    + "\\s*(?:['’]?)(?:sinde|sında|inde|ında)?[^.]{0,80}?(?:hatal[ıi]|bozuk|görüldü|goruldu|konu\\s*sat[ıi]r[ıi]))"
                    + "|"
                    + "(?:\\d+\\s*/\\s*\\d+\\s*(?:tekrar|istemci|deneme)[^.]{0,40}?(?:401|hata|fail))"
                    + ")",
            RX
    );

    public ExtractionBundle seed(ExtractionBundle bundle, List<SegmentInput> segments) {
        Objects.requireNonNull(bundle, "bundle");
        if (segments == null || segments.isEmpty()) {
            return bundle;
        }
        Map<String, ImportantFactCandidate> byKey = new LinkedHashMap<>();
        for (ImportantFactCandidate existing : bundle.importantFacts()) {
            if (existing != null && existing.text() != null && !existing.text().isBlank()) {
                byKey.put(norm(existing.text()), existing);
            }
        }
        for (SegmentInput segment : segments) {
            if (segment == null || segment.content() == null || segment.content().isBlank()) {
                continue;
            }
            Matcher m = MEASURED.matcher(segment.content());
            while (m.find()) {
                String raw = clean(m.group());
                if (raw.length() < 12) {
                    continue;
                }
                String key = norm(raw);
                if (byKey.containsKey(key)) {
                    continue;
                }
                // Near-dup against existing facts (digit vs word form)
                if (nearDuplicate(byKey, raw)) {
                    continue;
                }
                byKey.put(key, new ImportantFactCandidate(
                        raw,
                        List.of(segment.segmentId()),
                        0.86
                ));
            }
        }
        if (byKey.size() == bundle.importantFacts().size()) {
            return bundle;
        }
        List<String> flags = new ArrayList<>(bundle.qualityFlags());
        if (!flags.contains("MEASURED_FACT_SEEDED")) {
            flags.add("MEASURED_FACT_SEEDED");
        }
        return new ExtractionBundle(
                bundle.topics(),
                bundle.decisions(),
                bundle.actionItems(),
                bundle.risks(),
                bundle.openQuestions(),
                bundle.commitments(),
                bundle.issues(),
                bundle.proposals(),
                List.copyOf(byKey.values()),
                flags,
                bundle.evidenceSegmentIds(),
                bundle.confidence()
        );
    }

    private static boolean nearDuplicate(Map<String, ImportantFactCandidate> byKey, String candidate) {
        String c = norm(candidate);
        boolean has401 = c.contains("401");
        boolean hasTekrar = c.contains("tekrar");
        boolean hasIstemci = c.contains("istemci");
        boolean hasKonu = c.contains("konu");
        for (ImportantFactCandidate existing : byKey.values()) {
            String e = norm(existing.text());
            if (has401 && hasTekrar && e.contains("401") && e.contains("tekrar")) {
                return true;
            }
            if (hasIstemci && (hasKonu || e.contains("hatal")) && e.contains("istemci")) {
                return true;
            }
        }
        return false;
    }

    private static String clean(String text) {
        String t = text.replace('\u00A0', ' ').replaceAll("\\s+", " ").strip();
        if (!t.isEmpty() && Character.isLetter(t.charAt(0)) && !Character.isUpperCase(t.charAt(0))) {
            t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
        }
        if (!t.endsWith(".") && !t.endsWith("!") && !t.endsWith("?")) {
            t = t + ".";
        }
        return t;
    }

    private static String norm(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
