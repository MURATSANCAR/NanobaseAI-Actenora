package com.nanobaseai.actenora.aiprocessing.domain.pipeline.extraction;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.MeetingNoisePatterns;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
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
 * Deterministic open-question seeds from explicit transcript cues.
 * Stabilizes OQ recall when the LLM omits questions that are clearly asked.
 */
public final class OpenQuestionCueSeeder {

    private static final int RX = Pattern.CASE_INSENSITIVE
            | Pattern.UNICODE_CASE
            | Pattern.UNICODE_CHARACTER_CLASS;

    private static final Pattern EXPLICIT_LABEL = Pattern.compile(
            "(?iu)a[cç][ıi]k\\s+kalan\\s+soru\\s+şu\\s*:\\s*(.+)$"
    );
    private static final Pattern QUESTION_SENTENCE = Pattern.compile(
            "(?iu)([^.!?]{12,220}\\?)"
    );
    private static final Pattern WH_QUESTION = Pattern.compile(
            "(?iu)((?:hangi|kim|ne\\s+zaman|nas[ıi]l|nerede|kaç|ne\\s+olur)[^.!?]{8,180}\\?)"
    );
    private static final Pattern UNRESOLVED_WORK = Pattern.compile(
            "(?iu)(a[cç][ıi]k\\s+kalan\\s+soru|netle[sş]medi|cevaps[ıi]z|ne\\s+zaman|"
                    + "kim\\s+(?:sorumlu|g[oö]nderecek|[uü]stlenecek)|"
                    + "hangi\\s+(?:alarm|dashboard|metrik|log|kriter|ortam|versiyon|ad[ıi]m)|"
                    + "rollback|geri\\s+alma|k[oö]k\\s+neden|takvim|teslim\\s+tarihi|"
                    + "owner|deadline|unresolved|open\\s+question)"
    );

    public ExtractionBundle seed(ExtractionBundle bundle, List<SegmentInput> segments) {
        Objects.requireNonNull(bundle, "bundle");
        if (segments == null || segments.isEmpty()) {
            return bundle;
        }
        Map<String, OpenQuestionCandidate> byKey = new LinkedHashMap<>();
        for (OpenQuestionCandidate existing : bundle.openQuestions()) {
            if (existing != null && existing.text() != null && !existing.text().isBlank()) {
                byKey.put(norm(existing.text()), existing);
            }
        }
        int before = byKey.size();
        for (SegmentInput segment : segments) {
            if (segment == null || segment.content() == null || segment.content().isBlank()) {
                continue;
            }
            String content = segment.content().strip();
            // Skip pure meta / closing without substance
            if (isMetaOnly(content)
                    || MeetingNoisePatterns.isMeetingLogistics(content)
                    || MeetingNoisePatterns.isFacilitationOrPreparation(content)) {
                continue;
            }
            List<String> candidates = new ArrayList<>();
            java.util.Set<String> explicitlyLabeled = new java.util.HashSet<>();
            Matcher labeled = EXPLICIT_LABEL.matcher(content);
            if (labeled.find()) {
                String explicit = labeled.group(1).strip();
                candidates.add(explicit);
                explicitlyLabeled.add(norm(explicit));
            }
            Matcher wh = WH_QUESTION.matcher(content);
            while (wh.find()) {
                candidates.add(wh.group(1).strip());
            }
            if (candidates.isEmpty()) {
                Matcher q = QUESTION_SENTENCE.matcher(content);
                while (q.find()) {
                    String s = q.group(1).strip();
                    if (looksLikeOpenQuestion(s)) {
                        candidates.add(s);
                    }
                }
            }
            for (String raw : candidates) {
                String cleaned = clean(raw);
                boolean explicit = explicitlyLabeled.contains(norm(raw));
                if (cleaned.length() < 12 || (!explicit && !looksLikeOpenQuestion(cleaned))) {
                    continue;
                }
                String key = norm(cleaned);
                if (byKey.containsKey(key) || nearDuplicate(byKey, cleaned)) {
                    continue;
                }
                byKey.put(key, new OpenQuestionCandidate(
                        cleaned,
                        List.of(segment.segmentId()),
                        0.84
                ));
            }
        }
        if (byKey.size() == before) {
            return bundle;
        }
        List<String> flags = new ArrayList<>(bundle.qualityFlags());
        if (!flags.contains("OPEN_QUESTION_CUE_SEEDED")) {
            flags.add("OPEN_QUESTION_CUE_SEEDED");
        }
        return new ExtractionBundle(
                bundle.topics(),
                bundle.decisions(),
                bundle.actionItems(),
                bundle.risks(),
                List.copyOf(byKey.values()),
                bundle.commitments(),
                bundle.issues(),
                bundle.proposals(),
                bundle.importantFacts(),
                flags,
                bundle.evidenceSegmentIds(),
                bundle.confidence()
        );
    }

    private static boolean looksLikeOpenQuestion(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        if (t.length() < 12) {
            return false;
        }
        if (t.contains("a[cç]ık kalan soru") || t.contains("açık kalan soru")) {
            return true;
        }
        // A question asked during discussion is not automatically an unresolved meeting outcome.
        // Seed only questions that carry an explicit follow-up/control/ownership signal.
        return UNRESOLVED_WORK.matcher(t).find();
    }

    private static boolean isMetaOnly(String content) {
        String t = content.toLowerCase(Locale.ROOT);
        return (t.contains("kapanışa geç") || t.contains("gündemi bugün kapanmayan"))
                && !t.contains("?");
    }

    private static boolean nearDuplicate(Map<String, OpenQuestionCandidate> byKey, String candidate) {
        String c = norm(candidate);
        for (OpenQuestionCandidate existing : byKey.values()) {
            String e = norm(existing.text());
            if (tokenOverlap(e, c) >= 0.55d) {
                return true;
            }
        }
        return false;
    }

    private static double tokenOverlap(String a, String b) {
        String[] aa = a.split("\\s+");
        String[] bb = b.split("\\s+");
        java.util.Set<String> sa = new java.util.HashSet<>();
        java.util.Set<String> sb = new java.util.HashSet<>();
        for (String t : aa) {
            if (t.length() > 2) {
                sa.add(t);
            }
        }
        for (String t : bb) {
            if (t.length() > 2) {
                sb.add(t);
            }
        }
        if (sa.isEmpty() || sb.isEmpty()) {
            return 0;
        }
        int inter = 0;
        for (String t : sa) {
            if (sb.contains(t)) {
                inter++;
            }
        }
        return (double) inter / (double) Math.min(sa.size(), sb.size());
    }

    private static String clean(String text) {
        String t = text.replace('\u00A0', ' ').replaceAll("\\s+", " ").strip();
        t = t.replaceFirst("(?iu)^\\s*a[cç][ıi]k\\s+kalan\\s+soru\\s+şu\\s*:\\s*", "");
        if (!t.isEmpty() && Character.isLetter(t.charAt(0)) && !Character.isUpperCase(t.charAt(0))) {
            t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
        }
        if (!t.endsWith("?") && !t.endsWith(".") && !t.endsWith("!")) {
            t = t + "?";
        }
        return t;
    }

    private static String norm(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
