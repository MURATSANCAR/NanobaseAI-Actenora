package com.nanobaseai.actenora.aiprocessing.domain.pipeline.action;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic, source-aware action deduplication / subsumption.
 * Prefer atomic over compound, richer dates, stronger evidence; merge fields into survivor.
 */
public final class ActionDeduplicator {

    public static final String AMBIGUOUS_DEDUP = "AMBIGUOUS_ACTION_DEDUP";

    private final ActionIdentityNormalizer identity = new ActionIdentityNormalizer();

    public record Result(List<ActionItemCandidate> actions, int removed, List<String> warnings) {
    }

    public Result deduplicate(List<ActionItemCandidate> actions) {
        Objects.requireNonNull(actions, "actions");
        if (actions.size() <= 1) {
            return new Result(List.copyOf(actions), 0, List.of());
        }
        List<ActionItemCandidate> remaining = new ArrayList<>(actions);
        int removed = 0;
        List<String> warnings = new ArrayList<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            outer:
            for (int i = 0; i < remaining.size(); i++) {
                for (int j = i + 1; j < remaining.size(); j++) {
                    ActionItemCandidate a = remaining.get(i);
                    ActionItemCandidate b = remaining.get(j);
                    Match match = classify(a, b);
                    if (match == Match.NONE) {
                        continue;
                    }
                    if (match == Match.AMBIGUOUS) {
                        if (!warnings.contains(AMBIGUOUS_DEDUP)) {
                            warnings.add(AMBIGUOUS_DEDUP);
                        }
                        continue;
                    }
                    ActionItemCandidate survivor = merge(a, b);
                    remaining.remove(j);
                    remaining.remove(i);
                    remaining.add(i, survivor);
                    removed++;
                    changed = true;
                    break outer;
                }
            }
        }
        return new Result(List.copyOf(remaining), removed, warnings);
    }

    private enum Match {
        NONE,
        DUPLICATE,
        AMBIGUOUS
    }

    private Match classify(ActionItemCandidate a, ActionItemCandidate b) {
        if (!sameOwner(a, b)) {
            return Match.NONE;
        }
        boolean evidenceOverlap = evidenceOverlap(a.evidenceSegmentIds(), b.evidenceSegmentIds());
        String coreA = identity.canonicalCore(a);
        String coreB = identity.canonicalCore(b);
        if (coreA.isBlank() || coreB.isBlank()) {
            return Match.NONE;
        }
        boolean coreEqual = coreA.equals(coreB);
        boolean coreSimilar = coreEqual || tokenJaccard(coreA, coreB) >= 0.50d
                || containsCore(coreA, coreB) || containsCore(coreB, coreA);
        boolean compoundChild = looksCompound(a.text()) != looksCompound(b.text());
        if (coreSimilar && (evidenceOverlap || coreEqual || compoundChild)) {
            return Match.DUPLICATE;
        }
        // Same evidence + shared product/channel anchor with related stem or modest overlap
        if (evidenceOverlap && sharesDistinctiveAnchor(coreA, coreB)
                && (tokenJaccard(coreA, coreB) >= 0.40d || sharesRelatedStem(coreA, coreB))) {
            return Match.DUPLICATE;
        }
        if (coreSimilar && !evidenceOverlap) {
            return Match.AMBIGUOUS;
        }
        return Match.NONE;
    }

    /**
     * Shared long token (product/channel/system name) — used only with evidence overlap.
     */
    private static boolean sharesDistinctiveAnchor(String coreA, String coreB) {
        Set<String> ta = tokenSet(coreA);
        Set<String> tb = tokenSet(coreB);
        for (String a : ta) {
            if (a.length() < 6) {
                continue;
            }
            if (tb.contains(a)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sharesRelatedStem(String coreA, String coreB) {
        Set<String> ta = tokenSet(coreA);
        Set<String> tb = tokenSet(coreB);
        for (String a : ta) {
            if (a.length() < 4) {
                continue;
            }
            for (String b : tb) {
                if (b.length() < 4) {
                    continue;
                }
                if (a.equals(b)) {
                    continue;
                }
                int n = Math.min(a.length(), b.length());
                int i = 0;
                while (i < n && a.charAt(i) == b.charAt(i)) {
                    i++;
                }
                if (i >= 4) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> tokenSet(String core) {
        Set<String> set = new LinkedHashSet<>();
        for (String t : core.split("\\s+")) {
            if (t.length() >= 3) {
                set.add(t);
            }
        }
        return set;
    }

    private ActionItemCandidate merge(ActionItemCandidate a, ActionItemCandidate b) {
        ActionItemCandidate primary = prefer(a, b);
        ActionItemCandidate secondary = primary == a ? b : a;
        String text = preferText(primary, secondary);
        String owner = primary.owner() != null && !primary.owner().isBlank()
                ? primary.owner()
                : secondary.owner();
        String dueDate = firstNonBlank(primary.dueDate(), secondary.dueDate());
        String relative = firstNonBlank(primary.relativeDate(), secondary.relativeDate());
        String dueAt = firstNonBlank(primary.dueAt(), secondary.dueAt());
        if ((dueDate == null || dueDate.isBlank()) && dueAt != null && !dueAt.isBlank()) {
            try {
                dueDate = java.time.OffsetDateTime.parse(dueAt).toLocalDate().toString();
            } catch (RuntimeException ignored) {
                // Keep original dueDate null when dueAt is malformed.
            }
        }
        Set<String> evidence = new HashSet<>(primary.evidenceSegmentIds());
        evidence.addAll(secondary.evidenceSegmentIds());
        double confidence = Math.max(primary.confidence(), secondary.confidence());
        return new ActionItemCandidate(
                text,
                owner,
                dueDate,
                new ArrayList<>(evidence),
                confidence,
                firstNonBlank(primary.ownerType(), secondary.ownerType()),
                firstNonBlank(primary.priority(), secondary.priority()),
                relative,
                dueAt
        );
    }

    private ActionItemCandidate prefer(ActionItemCandidate a, ActionItemCandidate b) {
        int scoreA = score(a);
        int scoreB = score(b);
        if (scoreA != scoreB) {
            return scoreA >= scoreB ? a : b;
        }
        // Prefer shorter cleaned text
        return a.text().length() <= b.text().length() ? a : b;
    }

    private int score(ActionItemCandidate a) {
        int s = 0;
        if (!looksCompound(a.text())) {
            s += 8;
        }
        if (a.owner() != null && !a.owner().isBlank()) {
            s += 4;
        }
        if (a.dueAt() != null && !a.dueAt().isBlank()) {
            s += 4;
        } else if (a.dueDate() != null && !a.dueDate().isBlank()) {
            s += 3;
        } else if (a.relativeDate() != null && !a.relativeDate().isBlank()) {
            s += 2;
        }
        s += Math.min(3, a.evidenceSegmentIds().size());
        if (!ActionDiscoursePrefixNormalizer.lower(a.text()).startsWith("aksiyon")) {
            s += 1;
        }
        return s;
    }

    private String preferText(ActionItemCandidate primary, ActionItemCandidate secondary) {
        if (looksCompound(primary.text()) && !looksCompound(secondary.text())) {
            return secondary.text();
        }
        if (!looksCompound(primary.text()) && looksCompound(secondary.text())) {
            return primary.text();
        }
        // Prefer text without discourse prefix and with more substance tokens
        String p = primary.text();
        String s = secondary.text();
        if (new ActionDiscoursePrefixNormalizer().startsWithDiscoursePrefix(p)
                && !new ActionDiscoursePrefixNormalizer().startsWithDiscoursePrefix(s)) {
            return s;
        }
        return identity.canonicalCore(p).length() >= identity.canonicalCore(s).length() ? p : s;
    }

    static boolean looksCompound(String text) {
        return text != null && text.contains(";");
    }

    /**
     * Owners match when equal. Both blank also match so near-duplicates without roster
     * binding (unattributed transcripts) can still be merged by core + evidence.
     */
    boolean sameOwner(ActionItemCandidate a, ActionItemCandidate b) {
        String left = identity.canonicalOwner(a);
        String right = identity.canonicalOwner(b);
        if (left.isBlank() && right.isBlank()) {
            return true;
        }
        if (left.isBlank() || right.isBlank()) {
            return false;
        }
        return left.equals(right);
    }

    public static boolean evidenceOverlap(List<String> a, List<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        Set<String> set = new HashSet<>(a);
        for (String id : b) {
            if (set.contains(id)) {
                return true;
            }
        }
        return false;
    }

    String actionCore(String text) {
        return identity.canonicalCore(text);
    }

    private static boolean containsCore(String a, String b) {
        return a.contains(b) || b.contains(a);
    }

    private static double tokenJaccard(String a, String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0.0d;
        }
        Set<String> inter = new HashSet<>(ta);
        inter.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return (double) inter.size() / (double) union.size();
    }

    private static Set<String> tokens(String core) {
        Set<String> set = new HashSet<>();
        for (String t : core.split("\\s+")) {
            if (t.length() >= 3) {
                set.add(t);
            }
        }
        return set;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
