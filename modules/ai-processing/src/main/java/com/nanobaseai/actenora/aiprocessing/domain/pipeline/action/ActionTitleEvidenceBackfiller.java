package com.nanobaseai.actenora.aiprocessing.domain.pipeline.action;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Repairs ASR-truncated / incomplete action titles from evidence segment content.
 * Never invents work — only replaces when evidence supplies a longer complete clause.
 */
public final class ActionTitleEvidenceBackfiller {

    private static final int MIN_TITLE_LEN = 18;
    private static final int MAX_EVIDENCE_CHARS = 220;
    private static final Pattern ENDS_TRUNCATED = Pattern.compile(".*(…|\\.{2,}|\\u2026)\\s*$");
    private static final Pattern LIKELY_INCOMPLETE = Pattern.compile(
            "(?iu)^(yak[ıi]n|taban[ıi]na|e[gğ]itimi|hesaplanaca[gğ][ıi]|[uü]zerinden|"
                    + "arasında|grubunu|modelinin|verisine)\\b.*"
    );
    private static final Pattern HAS_VERBISH = Pattern.compile(
            "(?iu)\\b(oluştur|haz[ıi]rla|aktar|belirle|e[sş]le[sş]tir|konu[sş]|yap[ıi]l|"
                    + "kur|ara|payla[sş]|tamamla|yaz|ekle|g[uü]ncelle|sa[gğ]la)\\w*"
    );

    public List<ActionItemCandidate> backfill(
            List<ActionItemCandidate> actions,
            List<SegmentInput> segments
    ) {
        Objects.requireNonNull(actions, "actions");
        Map<String, String> byId = index(segments == null ? List.of() : segments);
        List<ActionItemCandidate> out = new ArrayList<>(actions.size());
        for (ActionItemCandidate action : actions) {
            if (!needsBackfill(action.text())) {
                out.add(action);
                continue;
            }
            String evidence = pickEvidenceSentence(action, byId);
            if (evidence == null) {
                out.add(action);
                continue;
            }
            String repaired = cleanEvidenceSentence(evidence);
            if (repaired == null || repaired.length() <= action.text().strip().length()) {
                out.add(action);
                continue;
            }
            // Only accept evidence that clearly extends the truncated stem
            String stem = action.text().strip().replaceAll("[.…\\u2026]+$", "").strip();
            String repairedLower = repaired.toLowerCase(Locale.ROOT);
            String stemLower = stem.toLowerCase(Locale.ROOT);
            if (!stem.isBlank() && stem.length() >= 8
                    && !repairedLower.contains(stemLower)
                    && !stemLower.contains(repairedLower.substring(0, Math.min(12, repairedLower.length())))) {
                out.add(action);
                continue;
            }
            if (!HAS_VERBISH.matcher(repaired).find() && HAS_VERBISH.matcher(action.text()).find()) {
                out.add(action);
                continue;
            }
            out.add(action.withText(repaired));
        }
        return List.copyOf(out);
    }

    static boolean needsBackfill(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String t = text.strip();
        if (t.length() < MIN_TITLE_LEN) {
            return true;
        }
        if (ENDS_TRUNCATED.matcher(t).matches()) {
            return true;
        }
        if (LIKELY_INCOMPLETE.matcher(t).matches() && !HAS_VERBISH.matcher(t).find()) {
            return true;
        }
        // Starts mid-phrase: lowercase connective / possessive stem after strip
        String first = firstWhitespaceToken(t).toLowerCase(Locale.ROOT);
        return LIKELY_INCOMPLETE.matcher(first + " x").matches() && t.length() < 80;
    }

    private static String firstWhitespaceToken(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        for (String token : text.strip().split("\\s+")) {
            if (!token.isBlank()) {
                return token;
            }
        }
        return "";
    }

    private static String pickEvidenceSentence(ActionItemCandidate action, Map<String, String> byId) {
        String best = null;
        for (String id : action.evidenceSegmentIds()) {
            String content = byId.get(id);
            if (content == null || content.isBlank()) {
                continue;
            }
            String sentence = cleanEvidenceSentence(content);
            if (sentence == null) {
                continue;
            }
            if (best == null || sentence.length() > best.length()) {
                best = sentence;
            }
        }
        return best;
    }

    static String cleanEvidenceSentence(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String t = content.replace('\u00A0', ' ').replaceAll("\\s+", " ").strip();
        if (t.length() > MAX_EVIDENCE_CHARS) {
            // Prefer first sentence-ish clause within limit
            int cut = Math.min(MAX_EVIDENCE_CHARS, t.length());
            int period = t.lastIndexOf('.', cut);
            int q = t.lastIndexOf('?', cut);
            int e = t.lastIndexOf('!', cut);
            int end = Math.max(period, Math.max(q, e));
            if (end >= MIN_TITLE_LEN) {
                t = t.substring(0, end + 1).strip();
            } else {
                t = t.substring(0, cut).strip();
            }
        }
        if (t.length() < MIN_TITLE_LEN) {
            return null;
        }
        // Refuse pure dialogue acknowledgements
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.matches("^(tamam|evet|h[ıi]h[ıi]|anlad[ıi]m|tabii).*") && t.length() < 40) {
            return null;
        }
        if (!Character.isUpperCase(t.charAt(0)) && Character.isLetter(t.charAt(0))) {
            t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
        }
        if (!t.endsWith(".") && !t.endsWith("?") && !t.endsWith("!")) {
            t = t + ".";
        }
        return t;
    }

    private static Map<String, String> index(List<SegmentInput> segments) {
        Map<String, String> map = new LinkedHashMap<>();
        for (SegmentInput s : segments) {
            if (s != null && s.segmentId() != null) {
                map.put(s.segmentId(), s.content());
            }
        }
        return map;
    }
}
