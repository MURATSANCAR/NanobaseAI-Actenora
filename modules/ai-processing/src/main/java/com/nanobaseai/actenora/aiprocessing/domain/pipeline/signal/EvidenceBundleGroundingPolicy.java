package com.nanobaseai.actenora.aiprocessing.domain.pipeline.signal;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.MeetingNoisePatterns;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.RiskCandidate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Central post-extract grounding. Soft-drops unsupported decisions here — not in merger/assembler/synth.
 */
public final class EvidenceBundleGroundingPolicy implements BundleGroundingPolicy {

    private static final Pattern POSITIVE_DECISION = Pattern.compile(
            "(?iu)(kararlaştır|karar\\s+verd|onayla|onaylandı|kabul\\s+ed|dondur|kapattık|kapatıyoruz|"
                    + "değişmeyecek|freeze|approve|decided|agreed|we\\s+will)"
    );
    private static final Pattern SCOPE_OR_DATE = Pattern.compile(
            "(?iu)(cuma|çarşamba|pazartesi|hafta|çeyrek|sprint|api|fiyat|politika|sözleşme|"
                    + "\\d{1,2}[./]\\d|next\\s+week|this\\s+quarter)"
    );
    private static final Pattern PROPOSAL_ONLY = Pattern.compile(
            "(?iu)\\b(belki|öner|should|could|might|erteleyebiliriz|değerlendirelim|henüz\\s+karar\\s+değil)\\b"
    );
    private static final Pattern META_DECISION = Pattern.compile(
            "(?iu)(karar\\s+ekran|kararları\\s+(tekrar\\s+)?oku|yeni\\s+(bir\\s+)?karar\\s+alınmadı)"
    );

    @Override
    public ExtractionBundle retainGroundedItems(ExtractionBundle bundle, EvidenceIndex evidenceIndex) {
        Objects.requireNonNull(bundle, "bundle");
        Objects.requireNonNull(evidenceIndex, "evidenceIndex");

        List<String> flags = new ArrayList<>(bundle.qualityFlags());
        List<DecisionCandidate> decisions = new ArrayList<>();
        int dropped = 0;

        for (DecisionCandidate decision : bundle.decisions()) {
            if (!evidenceIndex.allResolved(decision.evidenceSegmentIds())) {
                dropped++;
                addFlag(flags, "UNRESOLVED_EVIDENCE");
                continue;
            }
            String evidence = evidenceIndex.resolve(decision.evidenceSegmentIds());
            if (isGroundedDecision(decision.text(), evidence)) {
                decisions.add(decision);
            } else {
                dropped++;
                addFlag(flags, "UNSUPPORTED_DECISION");
            }
        }

        List<ActionItemCandidate> actions = filterResolved(bundle.actionItems(), evidenceIndex,
                ActionItemCandidate::evidenceSegmentIds, flags);
        List<RiskCandidate> risks = filterResolved(bundle.risks(), evidenceIndex,
                RiskCandidate::evidenceSegmentIds, flags);
        List<CommitmentCandidate> commitments = filterResolved(bundle.commitments(), evidenceIndex,
                CommitmentCandidate::evidenceSegmentIds, flags);
        List<OpenQuestionCandidate> questions = filterResolved(bundle.openQuestions(), evidenceIndex,
                OpenQuestionCandidate::evidenceSegmentIds, flags);

        if (dropped > 0) {
            addFlag(flags, "DECISION_SOFT_DROPPED");
        }

        return new ExtractionBundle(
                bundle.topics(),
                decisions,
                actions,
                risks,
                questions,
                commitments,
                bundle.issues(),
                bundle.proposals(),
                bundle.importantFacts(),
                flags,
                bundle.evidenceSegmentIds(),
                bundle.confidence()
        );
    }

    static boolean isGroundedDecision(String decisionText, String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return false;
        }
        String ev = evidence.toLowerCase(Locale.ROOT);
        String dt = decisionText == null ? "" : decisionText.toLowerCase(Locale.ROOT);

        if (META_DECISION.matcher(ev).find() && !POSITIVE_DECISION.matcher(ev).find()) {
            return false;
        }
        if (PROPOSAL_ONLY.matcher(ev).find() && !POSITIVE_DECISION.matcher(ev).find()
                && !closeOrFreeze(ev)) {
            return false;
        }
        if (POSITIVE_DECISION.matcher(ev).find() || closeOrFreeze(ev)) {
            return true;
        }
        if (MeetingNoisePatterns.isStatusQuoNonDecision(dt)
                || MeetingNoisePatterns.isStatusQuoNonDecision(ev)) {
            return POSITIVE_DECISION.matcher(ev).find() && SCOPE_OR_DATE.matcher(ev).find();
        }
        return tokenOverlap(dt, ev) >= 0.25d && !MeetingNoisePatterns.isLowSignalSegment(evidence);
    }

    private static boolean closeOrFreeze(String ev) {
        return ev.contains("dondur") || ev.contains("kapatıyoruz") || ev.contains("kapattık")
                || ev.contains("değişmeyecek") || ev.contains("freeze");
    }

    private static double tokenOverlap(String a, String b) {
        String[] ta = a.split("\\s+");
        int hits = 0;
        int meaningful = 0;
        for (String t : ta) {
            if (t.length() < 4) {
                continue;
            }
            meaningful++;
            if (b.contains(t)) {
                hits++;
            }
        }
        return meaningful == 0 ? 0.0d : (double) hits / (double) meaningful;
    }

    private static void addFlag(List<String> flags, String flag) {
        if (!flags.contains(flag)) {
            flags.add(flag);
        }
    }

    @FunctionalInterface
    private interface EvidenceAccessor<T> {
        List<String> evidence(T item);
    }

    private static <T> List<T> filterResolved(
            List<T> items,
            EvidenceIndex index,
            EvidenceAccessor<T> accessor,
            List<String> flags
    ) {
        List<T> kept = new ArrayList<>(items.size());
        for (T item : items) {
            if (index.allResolved(accessor.evidence(item))) {
                kept.add(item);
            } else {
                addFlag(flags, "UNRESOLVED_EVIDENCE");
            }
        }
        return kept;
    }
}
