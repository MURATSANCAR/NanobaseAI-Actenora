package com.nanobaseai.actenora.aiprocessing.domain.pipeline.composer;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ProposalCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.RiskCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal.EvidenceBundleGroundingPolicy;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal.EvidenceIndex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Referential + semantic grounding for composer candidates. Independently grounded —
 * ledger membership is not required.
 */
public final class GlobalCompositionAuditor {

    public static final String FLAG_COMPOSER_EVIDENCE_REJECTED = "COMPOSER_EVIDENCE_REJECTED";
    public static final String FLAG_COMPOSER_HIGH_REJECTION = "COMPOSER_HIGH_REJECTION";

    private static final Pattern OWNER_GRAMMAR = Pattern.compile(
            "(?iu)\\b([\\p{L}][\\p{L}\\s.'-]{1,40}?)\\s+(yapaca[gğ][ıi]m|edece[gğ]im|g[oö]nderece[gğ]im|"
                    + "payla[sş]aca[gğ][ıi]m|iletece[gğ]im|organize\\s+edece[gğ]im)\\b"
    );
    private static final Pattern SUBJECT_DOES = Pattern.compile(
            "(?iu)\\b([\\p{L}][\\p{L}.'-]{1,40})\\s+(bunu\\s+)?(g[oö]nderir|g[oö]nderecek|yapacak|edecek|"
                    + "iletir|organize\\s+eder|payla[sş][ıi]r)\\b"
    );
    private static final Pattern WITH_PERSON = Pattern.compile(
            "(?iu)\\b(ben|biz)\\b.{0,60}?([\\p{L}][\\p{L}\\s.'-]{1,40}?)['’]?\\s*(ile|la|le)\\s+"
                    + "(konu[sş]|g[oö]r[uü][sş]|konu[sş]aca|g[oö]r[uü][sş]ece)"
    );
    private static final Pattern FIRST_PERSON = Pattern.compile(
            "(?iu)\\b(ben|ben\\s+de)\\b.{0,40}\\b(yapaca[gğ][ıi]m|edece[gğ]im|g[oö]r[uü][sş]ece[gğ]im|"
                    + "konu[sş]aca[gğ][ıi]m)\\b"
    );
    private static final double HIGH_REJECTION_RATIO = 0.65d;

    public VerifiedComposition verify(
            GlobalComposition composition,
            List<SegmentInput> segments,
            Set<String> roster,
            Set<String> allowedEvidenceIds
    ) {
        Objects.requireNonNull(composition, "composition");
        Objects.requireNonNull(segments, "segments");
        Set<String> allowed = allowedEvidenceIds == null ? Set.of() : Set.copyOf(allowedEvidenceIds);
        Set<String> people = roster == null ? Set.of() : Set.copyOf(roster);
        EvidenceIndex index = EvidenceIndex.from(segments);

        List<String> flags = new ArrayList<>();
        GlobalComposition.MeetingFrame frame = composition.meetingFrame();
        GlobalComposition.MeetingFrame verifiedFrame = null;
        if (frame != null
                && referentialOk(frame.evidenceSegmentIds(), allowed)
                && !frame.text().isBlank()) {
            verifiedFrame = frame;
        }

        List<GlobalComposition.GlobalCandidate> accepted = new ArrayList<>();
        int rejected = 0;
        for (GlobalComposition.GlobalCandidate candidate : composition.candidates()) {
            if (!referentialOk(candidate.evidenceSegmentIds(), allowed)) {
                rejected++;
                continue;
            }
            String evidence = index.resolve(candidate.evidenceSegmentIds());
            if (evidence.isBlank()) {
                rejected++;
                continue;
            }
            GlobalComposition.GlobalCandidate normalized = sanitizeOwner(candidate, evidence, people, segments);
            if (!semanticallyGrounded(normalized, evidence)) {
                rejected++;
                continue;
            }
            accepted.add(normalized);
        }
        if (rejected > 0) {
            flags.add(FLAG_COMPOSER_EVIDENCE_REJECTED);
        }
        int total = composition.candidates().size();
        boolean highRejection = total > 0 && ((double) rejected / (double) total) >= HIGH_REJECTION_RATIO;
        if (highRejection) {
            flags.add(FLAG_COMPOSER_HIGH_REJECTION);
        }
        return new VerifiedComposition(verifiedFrame, List.copyOf(accepted), flags, highRejection);
    }

    private static boolean referentialOk(List<String> ids, Set<String> allowed) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        for (String id : ids) {
            if (id == null || id.isBlank() || !allowed.contains(id)) {
                return false;
            }
        }
        return true;
    }

    private static boolean semanticallyGrounded(GlobalComposition.GlobalCandidate candidate, String evidence) {
        return switch (candidate.type()) {
            case DECISION -> EvidenceBundleGroundingPolicy.isGroundedDecision(candidate.text(), evidence);
            case ACTION, COMMITMENT -> evidenceSupportsAction(candidate.text(), evidence);
            case OPEN_QUESTION -> evidence.contains("?")
                    || evidence.toLowerCase(Locale.ROOT).contains("nedir")
                    || evidence.toLowerCase(Locale.ROOT).contains("nasıl");
            case RISK, IMPORTANT_FACT, PROPOSAL -> tokenOverlap(candidate.text(), evidence) >= 0.20d;
        };
    }

    private static boolean evidenceSupportsAction(String text, String evidence) {
        if (tokenOverlap(text, evidence) < 0.15d) {
            return false;
        }
        String ev = evidence.toLowerCase(Locale.ROOT);
        return FUTURE_OR_COMMIT.matcher(ev).find() || tokenOverlap(text, evidence) >= 0.35d;
    }

    private static final Pattern FUTURE_OR_COMMIT = Pattern.compile(
            "(?iu)(yapaca[gğ]|edece[gğ]|g[oö]nder|payla[sş]|organize|eyl[uü]l|hafta|iletece|"
                    + "toplant[ıi]|takip|i\\s+will|we\\s+will)"
    );

    private static GlobalComposition.GlobalCandidate sanitizeOwner(
            GlobalComposition.GlobalCandidate candidate,
            String evidence,
            Set<String> roster,
            List<SegmentInput> segments
    ) {
        if (candidate.type() != GlobalComposition.CandidateType.ACTION
                && candidate.type() != GlobalComposition.CandidateType.COMMITMENT) {
            return candidate;
        }
        String owner = resolveOwner(candidate, evidence, roster, segments);
        return new GlobalComposition.GlobalCandidate(
                candidate.type(),
                candidate.text(),
                owner,
                candidate.dueDateText(),
                candidate.dueDateNormalized(),
                candidate.evidenceSegmentIds(),
                candidate.source(),
                candidate.confidence()
        );
    }

    /**
     * Owner priority: grammatical self → first-person speaker → subject-does →
     * with-person (speaker owns, mentioned person is collaborator) → candidate if roster → null.
     */
    static String resolveOwner(
            GlobalComposition.GlobalCandidate candidate,
            String evidence,
            Set<String> roster,
            List<SegmentInput> segments
    ) {
        var grammar = OWNER_GRAMMAR.matcher(evidence);
        if (grammar.find()) {
            String name = grammar.group(1).strip();
            String resolved = matchRoster(name, roster);
            if (resolved != null) {
                return resolved;
            }
        }
        if (FIRST_PERSON.matcher(evidence).find() || WITH_PERSON.matcher(evidence).find()) {
            String speaker = speakerForEvidence(candidate.evidenceSegmentIds(), segments);
            String resolved = matchRoster(speaker, roster);
            if (resolved != null) {
                return resolved;
            }
            if (speaker != null && !speaker.isBlank()) {
                return speaker;
            }
        }
        var subject = SUBJECT_DOES.matcher(evidence);
        if (subject.find()) {
            String name = subject.group(1).strip();
            String resolved = matchRoster(name, roster);
            if (resolved != null) {
                return resolved;
            }
        }
        String candidateOwner = candidate.ownerCandidate();
        if (candidateOwner != null && !candidateOwner.isBlank()) {
            // Collaborator mention must not become owner when first-person/with-person applies.
            if (evidence.toLowerCase(Locale.ROOT).matches("(?s).*\\b(ben|biz)\\b.*")
                    && evidence.toLowerCase(Locale.ROOT).contains("ile")) {
                String speaker = speakerForEvidence(candidate.evidenceSegmentIds(), segments);
                String resolved = matchRoster(speaker, roster);
                if (resolved != null) {
                    return resolved;
                }
            }
            return matchRoster(candidateOwner, roster);
        }
        return null;
    }

    private static String speakerForEvidence(List<String> ids, List<SegmentInput> segments) {
        Set<String> want = new HashSet<>(ids);
        for (SegmentInput segment : segments) {
            if (want.contains(segment.segmentId())
                    && segment.speakerDisplayName() != null
                    && !segment.speakerDisplayName().isBlank()) {
                return segment.speakerDisplayName();
            }
        }
        return null;
    }

    private static String matchRoster(String name, Set<String> roster) {
        if (name == null || name.isBlank() || roster == null || roster.isEmpty()) {
            return null;
        }
        String needle = name.strip().toLowerCase(Locale.ROOT);
        for (String person : roster) {
            if (person == null || person.isBlank()) {
                continue;
            }
            String p = person.strip().toLowerCase(Locale.ROOT);
            if (p.equals(needle) || p.contains(needle) || needle.contains(p)) {
                return person.strip();
            }
        }
        return null;
    }

    private static double tokenOverlap(String a, String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0.0d;
        }
        int hits = 0;
        for (String t : ta) {
            if (tb.contains(t)) {
                hits++;
            }
        }
        return (double) hits / (double) ta.size();
    }

    private static Set<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) {
            return out;
        }
        for (String t : text.toLowerCase(Locale.ROOT).split("\\s+")) {
            String n = t.replaceAll("[^\\p{L}\\p{N}]+", "");
            if (n.length() >= 3) {
                out.add(n);
            }
        }
        return out;
    }

    public record VerifiedComposition(
            GlobalComposition.MeetingFrame meetingFrame,
            List<GlobalComposition.GlobalCandidate> acceptedItems,
            List<String> qualityFlags,
            boolean highRejection
    ) {
        public VerifiedComposition {
            acceptedItems = List.copyOf(Objects.requireNonNullElse(acceptedItems, List.of()));
            qualityFlags = List.copyOf(Objects.requireNonNullElse(qualityFlags, List.of()));
        }
    }
}
