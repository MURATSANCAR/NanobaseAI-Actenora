package com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Post-synthesis cross-type consistency audit.
 * Matrix: D⊃P drop; D answers OQ → OQ drop; Action ⊃ next-step/fact → fact drop;
 * Action/OQ, Risk/Proposal, Risk/Action keep both.
 */
public final class CrossTypeConsistencyAuditor {

    public static final String AUDIT_PASSED = "CONSISTENCY_AUDIT_PASSED";
    public static final String AUDIT_NEEDS_REVIEW = "CONSISTENCY_AUDIT_NEEDS_REVIEW";
    public static final String AUDIT_STATUS_PREFIX = "auditStatus=";
    public static final String UNRESOLVED_COUNT_PREFIX = "unresolvedConflictCount=";
    public static final String GENERIC_ACTION_COUNT_PREFIX = "genericActionCount=";
    public static final String UNSUPPORTED_COUNT_PREFIX = "unsupportedItemCount=";
    public static final String FALLBACK_USED_PREFIX = "fallbackUsed=";

    private final DecisionProposalSubsumer subsumer = new DecisionProposalSubsumer();

    public FinalNoteDraft audit(FinalNoteDraft draft) {
        Objects.requireNonNull(draft, "draft");
        FinalNoteDraft afterDp = subsumer.applyToDraft(draft);
        List<OpenQuestionCandidate> questions = dropAnsweredQuestions(
                afterDp.decisions(), afterDp.openQuestions());
        // Action ⊃ checkpoint/next-step facts (importantFacts stand in until nextSteps persist).
        List<ImportantFactCandidate> facts = dropFactsCoveredByActions(
                afterDp.actionItems(), afterDp.importantFacts());

        Set<String> flags = new LinkedHashSet<>(afterDp.qualityFlags());
        int unresolved = flags.contains(DecisionProposalSubsumer.UNRESOLVED) ? 1 : 0;
        // Count multiple unresolved markers if present as detail suffixes — keep ≥1 when flag set.
        for (String f : flags) {
            if (f != null && f.toUpperCase(Locale.ROOT).contains(DecisionProposalSubsumer.UNRESOLVED)) {
                unresolved = Math.max(unresolved, 1);
            }
        }

        int genericActions = 0;
        for (ActionItemCandidate a : afterDp.actionItems()) {
            if (ActionContextualEnricher.isGenericAction(a.text())) {
                genericActions++;
            }
        }
        int unsupported = countUnsupported(flags);

        boolean fallbackUsed = flags.stream().anyMatch(f -> f != null && (
                f.equalsIgnoreCase("SYNTHESIS_FALLBACK") || f.equalsIgnoreCase("AUDIT_FALLBACK")));

        flags.removeIf(f -> f != null && (
                f.startsWith(UNRESOLVED_COUNT_PREFIX)
                        || f.startsWith(GENERIC_ACTION_COUNT_PREFIX)
                        || f.startsWith(UNSUPPORTED_COUNT_PREFIX)
                        || f.startsWith(AUDIT_STATUS_PREFIX)
                        || f.startsWith(FALLBACK_USED_PREFIX)
                        || f.equals(AUDIT_PASSED)
                        || f.equals(AUDIT_NEEDS_REVIEW)));

        flags.add(UNRESOLVED_COUNT_PREFIX + unresolved);
        flags.add(GENERIC_ACTION_COUNT_PREFIX + genericActions);
        flags.add(UNSUPPORTED_COUNT_PREFIX + unsupported);
        flags.add(FALLBACK_USED_PREFIX + fallbackUsed);
        if (unresolved > 0) {
            flags.add(AUDIT_NEEDS_REVIEW);
            flags.add(AUDIT_STATUS_PREFIX + "NEEDS_REVIEW");
        } else {
            flags.add(AUDIT_PASSED);
            flags.add("DECISION_CONSISTENCY_AUDIT_PASSED");
            flags.add(AUDIT_STATUS_PREFIX + "PASSED");
        }

        boolean manual = afterDp.requiresManualReview() || unresolved > 0;
        if (manual) {
            flags.add("REQUIRES_MANUAL_REVIEW");
        }

        return new FinalNoteDraft(
                afterDp.executiveSummary(),
                afterDp.decisions(),
                afterDp.actionItems(),
                afterDp.risks(),
                questions,
                afterDp.commitments(),
                afterDp.topics(),
                afterDp.issues(),
                afterDp.proposals(),
                facts,
                new ArrayList<>(flags),
                afterDp.evidenceSegmentIds(),
                afterDp.confidence(),
                manual
        );
    }

    public ExtractionBundle auditBundle(ExtractionBundle bundle) {
        return subsumer.applyToBundle(bundle);
    }

    private static List<OpenQuestionCandidate> dropAnsweredQuestions(
            List<DecisionCandidate> decisions,
            List<OpenQuestionCandidate> questions
    ) {
        List<OpenQuestionCandidate> kept = new ArrayList<>();
        for (OpenQuestionCandidate q : questions) {
            SemanticCore qCore = SemanticCore.extract(ItemTextViews.comparisonCore(q.text()));
            boolean answered = false;
            for (DecisionCandidate d : decisions) {
                SemanticCore dCore = SemanticCore.extract(ItemTextViews.comparisonCore(d.text()));
                if (dCore.topicSimilarity(qCore) >= 0.65d
                        && dCore.scopeCompatible(qCore)
                        && dCore.polarityCompatible(qCore)
                        && d.confidence() >= 0.7d) {
                    answered = true;
                    break;
                }
            }
            if (!answered) {
                kept.add(q);
            }
        }
        return kept;
    }

    /**
     * Same follow-up work in action + checkpoint/fact → keep action, drop fact.
     * Risk/proposal and action/OQ pairs are intentionally not auto-dropped.
     */
    private static List<ImportantFactCandidate> dropFactsCoveredByActions(
            List<ActionItemCandidate> actions,
            List<ImportantFactCandidate> facts
    ) {
        if (facts == null || facts.isEmpty() || actions == null || actions.isEmpty()) {
            return facts == null ? List.of() : facts;
        }
        List<ImportantFactCandidate> kept = new ArrayList<>();
        for (ImportantFactCandidate fact : facts) {
            SemanticCore fCore = SemanticCore.extract(ItemTextViews.comparisonCore(fact.text()));
            boolean covered = false;
            for (ActionItemCandidate action : actions) {
                SemanticCore aCore = SemanticCore.extract(ItemTextViews.comparisonCore(action.text()));
                if (aCore.topicSimilarity(fCore) >= 0.70d
                        && aCore.actionSimilarity(fCore) >= 0.55d
                        && aCore.scopeCompatible(fCore)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                kept.add(fact);
            }
        }
        return kept;
    }

    private static int countUnsupported(Set<String> flags) {
        int n = 0;
        for (String f : flags) {
            if (f == null) {
                continue;
            }
            String u = f.toUpperCase(Locale.ROOT);
            if (u.contains("EVIDENCE_REF_DROPPED")
                    || u.contains("UNSUPPORTED")
                    || u.contains("MISSING_EVIDENCE")
                    || u.contains("SOFT_DROP")) {
                n++;
            }
        }
        return n;
    }
}
