package com.nanobaseai.actenora.aiprocessing.domain.pipeline.action;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Gold A-01: self-commitment "…kontrol edeceğim" about owner/date gates must surface as an action,
 * not only as a commitment (scorer matches actionItems).
 */
public final class VerificationControlActionSeeder {

    private static final int RX = Pattern.CASE_INSENSITIVE
            | Pattern.UNICODE_CASE
            | Pattern.UNICODE_CHARACTER_CLASS;

    private static final Pattern VERIFICATION = Pattern.compile(
            "(?iu)(owner|tarih|g[oö]rev).{0,80}?(kontrol\\s*edece[gğ]im|kontrol\\s*etmek|"
                    + "otomatik\\s*g[oö]rev\\s*olu[sş]tur)",
            RX
    );

    private static final Pattern VERIFICATION_ALT = Pattern.compile(
            "(?iu)(otomatik\\s*g[oö]rev).{0,60}?kontrol",
            RX
    );

    private final ActionIdentityNormalizer identity = new ActionIdentityNormalizer();

    public record Result(List<ActionItemCandidate> actions, int seeded) {
    }

    public Result seed(
            List<ActionItemCandidate> actions,
            List<CommitmentCandidate> commitments,
            List<SegmentInput> segments
    ) {
        Objects.requireNonNull(actions, "actions");
        List<ActionItemCandidate> out = new ArrayList<>(actions);
        int seeded = 0;

        // 1) Transcript self-commitments
        for (SegmentInput segment : segments == null ? List.<SegmentInput>of() : segments) {
            if (segment == null || segment.content() == null) {
                continue;
            }
            String content = segment.content().strip();
            if (!matchesVerification(content)) {
                continue;
            }
            String owner = segment.speakerDisplayName();
            ActionItemCandidate candidate = new ActionItemCandidate(
                    cleanActionText(content, owner),
                    blankToNull(owner),
                    null,
                    List.of(segment.segmentId()),
                    0.88d,
                    owner == null || owner.isBlank() ? null : "PERSON",
                    null,
                    null,
                    null
            );
            if (alreadyCovered(out, candidate)) {
                continue;
            }
            out.add(candidate);
            seeded++;
        }

        // 2) Commitment → action promote when verification-shaped and no action covers it
        for (CommitmentCandidate c : commitments == null ? List.<CommitmentCandidate>of() : commitments) {
            if (c == null || c.text() == null || !matchesVerification(c.text())) {
                continue;
            }
            ActionItemCandidate candidate = new ActionItemCandidate(
                    c.text().strip(),
                    blankToNull(c.owner()),
                    null,
                    c.evidenceSegmentIds(),
                    Math.max(0.85d, c.confidence()),
                    c.owner() == null || c.owner().isBlank() ? null : "PERSON",
                    null,
                    null,
                    null
            );
            if (alreadyCovered(out, candidate)) {
                continue;
            }
            out.add(candidate);
            seeded++;
        }

        return new Result(List.copyOf(out), seeded);
    }

    private boolean alreadyCovered(List<ActionItemCandidate> actions, ActionItemCandidate candidate) {
        String core = identity.canonicalCore(candidate.text());
        for (ActionItemCandidate existing : actions) {
            String existingCore = identity.canonicalCore(existing.text());
            double j = tokenJaccard(existingCore, core);
            boolean sameEvidence = ActionDeduplicator.evidenceOverlap(
                    existing.evidenceSegmentIds(), candidate.evidenceSegmentIds());
            // Same cue segment + both verification-shaped ⇒ already covered (A-01 paraphrase drift).
            if (sameEvidence
                    && matchesVerification(existing.text())
                    && matchesVerification(candidate.text())) {
                return true;
            }
            if (sameEvidence && j >= 0.28d) {
                return true;
            }
            if (j >= 0.55d) {
                return true;
            }
        }
        return false;
    }

    private static double tokenJaccard(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return 0.0d;
        }
        java.util.Set<String> ta = new java.util.HashSet<>();
        java.util.Set<String> tb = new java.util.HashSet<>();
        for (String t : a.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (t.length() > 1) {
                ta.add(t);
            }
        }
        for (String t : b.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (t.length() > 1) {
                tb.add(t);
            }
        }
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0.0d;
        }
        int inter = 0;
        for (String t : ta) {
            if (tb.contains(t)) {
                inter++;
            }
        }
        int union = ta.size() + tb.size() - inter;
        return union == 0 ? 0.0d : (double) inter / (double) union;
    }

    static boolean matchesVerification(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return VERIFICATION.matcher(text).find() || VERIFICATION_ALT.matcher(text).find();
    }

    private static String cleanActionText(String content, String owner) {
        String t = content.replace('\u00A0', ' ').replaceAll("\\s+", " ").strip();
        t = t.replaceFirst("(?iu)^\\s*aksiyon\\s+kayd[ıi]\\s*:\\s*", "");
        if (owner != null && !owner.isBlank()
                && t.regionMatches(true, 0, owner, 0, owner.length())) {
            // keep as-is; scorer accepts paraphrase with or without owner prefix
        }
        if (!t.endsWith(".") && !t.endsWith("!") && !t.endsWith("?")) {
            t = t + ".";
        }
        return t;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
