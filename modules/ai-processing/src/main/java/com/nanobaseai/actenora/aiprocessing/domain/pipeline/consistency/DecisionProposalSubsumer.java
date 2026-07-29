package com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ProposalCandidate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Multi-signal decision ⊃ proposal subsumption. Successful drops never force RMR.
 */
public final class DecisionProposalSubsumer {

    public static final String DROPPED = "DECISION_SUBSUMED_PROPOSAL_DROPPED";
    public static final String UNRESOLVED = "UNRESOLVED_DECISION_PROPOSAL_CONFLICT";

    public static final double TOPIC_THRESHOLD = 0.55d;
    public static final double ACTION_THRESHOLD = 0.55d;
    public static final double DECISION_CONF_MIN = 0.70d;
    /** Topic similarity high enough to consider conflict but not safe to drop. */
    public static final double TOPIC_REVIEW_THRESHOLD = 0.40d;

    public record Outcome(List<ProposalCandidate> proposals, List<String> flags, int dropped, int unresolved) {
    }

    public Outcome apply(List<DecisionCandidate> decisions, List<ProposalCandidate> proposals) {
        Objects.requireNonNull(decisions, "decisions");
        Objects.requireNonNull(proposals, "proposals");
        List<ProposalCandidate> kept = new ArrayList<>();
        Set<String> flags = new LinkedHashSet<>();
        int dropped = 0;
        int unresolved = 0;

        List<SemanticCore> decisionCores = new ArrayList<>();
        List<Double> decisionConfs = new ArrayList<>();
        for (DecisionCandidate d : decisions) {
            decisionCores.add(SemanticCore.extract(ItemTextViews.comparisonCore(d.text())));
            decisionConfs.add(d.confidence());
        }

        for (ProposalCandidate proposal : proposals) {
            SemanticCore pCore = SemanticCore.extract(ItemTextViews.comparisonCore(proposal.text()));
            MatchKind kind = MatchKind.NONE;
            for (int i = 0; i < decisionCores.size(); i++) {
                MatchKind m = classify(decisionCores.get(i), decisionConfs.get(i), pCore);
                if (m == MatchKind.SAFE_DROP) {
                    kind = MatchKind.SAFE_DROP;
                    break;
                }
                if (m == MatchKind.UNRESOLVED) {
                    kind = MatchKind.UNRESOLVED;
                }
            }
            if (kind == MatchKind.SAFE_DROP) {
                dropped++;
                flags.add(DROPPED);
            } else {
                kept.add(proposal);
                if (kind == MatchKind.UNRESOLVED) {
                    unresolved++;
                    flags.add(UNRESOLVED);
                }
            }
        }
        return new Outcome(kept, List.copyOf(flags), dropped, unresolved);
    }

    public ExtractionBundle applyToBundle(ExtractionBundle bundle) {
        Outcome outcome = apply(bundle.decisions(), bundle.proposals());
        Set<String> flags = new LinkedHashSet<>(bundle.qualityFlags());
        flags.addAll(outcome.flags());
        return new ExtractionBundle(
                bundle.topics(),
                bundle.decisions(),
                bundle.actionItems(),
                bundle.risks(),
                bundle.openQuestions(),
                bundle.commitments(),
                bundle.issues(),
                outcome.proposals(),
                bundle.importantFacts(),
                new ArrayList<>(flags),
                bundle.evidenceSegmentIds(),
                bundle.confidence()
        );
    }

    public FinalNoteDraft applyToDraft(FinalNoteDraft draft) {
        Outcome outcome = apply(draft.decisions(), draft.proposals());
        Set<String> flags = new LinkedHashSet<>(draft.qualityFlags());
        flags.addAll(outcome.flags());
        // Successful drop never forces RMR; unresolved may.
        boolean manual = draft.requiresManualReview() || outcome.unresolved() > 0;
        if (manual) {
            flags.add("REQUIRES_MANUAL_REVIEW");
        }
        if (outcome.unresolved() == 0) {
            // Do not keep RMR solely because of successful drops — strip only if we added it here
            // and no other review signals; leave existing RMR from fallbacks untouched if other flags.
        }
        if (outcome.unresolved() > 0) {
            flags.add("CONSISTENCY_AUDIT_NEEDS_REVIEW");
        } else if (outcome.dropped() > 0) {
            flags.add("CONSISTENCY_AUDIT_PASSED");
        }
        return new FinalNoteDraft(
                draft.executiveSummary(),
                draft.decisions(),
                draft.actionItems(),
                draft.risks(),
                draft.openQuestions(),
                draft.commitments(),
                draft.topics(),
                draft.issues(),
                outcome.proposals(),
                draft.importantFacts(),
                new ArrayList<>(flags),
                draft.evidenceSegmentIds(),
                draft.confidence(),
                manual
        );
    }

    MatchKind classify(SemanticCore decision, double decisionConfidence, SemanticCore proposal) {
        double topic = decision.topicSimilarity(proposal);
        double action = decision.actionSimilarity(proposal);
        boolean polarityOk = decision.polarityCompatible(proposal);
        boolean scopeOk = decision.scopeCompatible(proposal);

        if (!polarityOk) {
            // Opposite polarity: never drop; leave both (tension is informative, not auto-review).
            return MatchKind.NONE;
        }
        if (!scopeOk) {
            return MatchKind.NONE;
        }
        if (topic >= TOPIC_THRESHOLD
                && action >= ACTION_THRESHOLD
                && decisionConfidence >= DECISION_CONF_MIN) {
            return MatchKind.SAFE_DROP;
        }
        if (topic >= TOPIC_REVIEW_THRESHOLD && action >= 0.35d) {
            return MatchKind.UNRESOLVED;
        }
        return MatchKind.NONE;
    }

    enum MatchKind {
        NONE,
        SAFE_DROP,
        UNRESOLVED
    }
}
