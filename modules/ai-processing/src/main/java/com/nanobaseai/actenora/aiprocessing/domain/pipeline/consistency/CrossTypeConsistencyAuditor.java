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
    public static final String FINALIZATION_FALLBACK = "FINALIZATION_FALLBACK";

    private final DecisionProposalSubsumer subsumer = new DecisionProposalSubsumer();

    public FinalNoteDraft audit(FinalNoteDraft draft) {
        return audit(draft, false);
    }

    public FinalNoteDraft audit(FinalNoteDraft draft, boolean finalizationFallbackUsed) {
        Objects.requireNonNull(draft, "draft");
        FinalNoteDraft afterDp = subsumer.applyToDraft(draft);
        List<OpenQuestionCandidate> questions = dropAnsweredQuestions(
                afterDp.decisions(), afterDp.openQuestions());
        List<String> hygieneFlags = new ArrayList<>();
        questions = new OpenQuestionHygieneFilter().filter(
                questions,
                afterDp.decisions(),
                afterDp.actionItems(),
                afterDp.commitments(),
                hygieneFlags
        );
        // Action ⊃ checkpoint/next-step facts (importantFacts stand in until nextSteps persist).
        List<ImportantFactCandidate> facts = dropFactsCoveredByActions(
                afterDp.actionItems(), afterDp.importantFacts());

        Set<String> flags = new LinkedHashSet<>(afterDp.qualityFlags());
        flags.addAll(hygieneFlags);
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

        if (finalizationFallbackUsed) {
            flags.add(FINALIZATION_FALLBACK);
        }
        boolean fallbackUsed = flags.stream().anyMatch(f -> f != null
                && f.toUpperCase(Locale.ROOT).endsWith("_FALLBACK"));

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
        boolean needsReview = unresolved > 0 || genericActions > 0 || unsupported > 0 || fallbackUsed;
        if (needsReview) {
            flags.add(AUDIT_NEEDS_REVIEW);
            flags.add(AUDIT_STATUS_PREFIX + "NEEDS_REVIEW");
        } else {
            flags.add(AUDIT_PASSED);
            flags.add("DECISION_CONSISTENCY_AUDIT_PASSED");
            flags.add(AUDIT_STATUS_PREFIX + "PASSED");
        }

        boolean manual = afterDp.requiresManualReview() || needsReview;
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
                // Same-topic decisions must not erase open questions. Only drop when the
                // decision is a high-confidence, high-overlap answer to the same ask.
                if (dCore.topicSimilarity(qCore) >= 0.82d
                        && dCore.actionSimilarity(qCore) >= 0.55d
                        && dCore.scopeCompatible(qCore)
                        && dCore.polarityCompatible(qCore)
                        && d.confidence() >= 0.85d) {
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
            // Measured observations (n/m rates, error counts) are not "covered" by a fix action.
            if (looksLikeMeasuredObservation(fact.text())) {
                kept.add(fact);
                continue;
            }
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

    /** Gold F-01/F-02 style: quantified test/error observations must survive action subsumption. */
    private static boolean looksLikeMeasuredObservation(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = text.toLowerCase(Locale.ROOT);
        boolean hasNumber = t.chars().anyMatch(Character::isDigit);
        if (!hasNumber) {
            return false;
        }
        return t.contains("tekrar")
                || t.contains("istemci")
                || t.contains("görüldü")
                || t.contains("goruldu")
                || t.contains("hata")
                || t.contains("401")
                || t.contains("oran")
                || t.contains("test edilen")
                || t.contains("test yapılan")
                || t.contains("test yapilan");
    }

    private static int countUnsupported(Set<String> flags) {
        int n = 0;
        for (String f : flags) {
            if (f == null) {
                continue;
            }
            String u = f.toUpperCase(Locale.ROOT);
            // Intentional soft-drops (EVIDENCE_ITEM_SOFT_DROPPED / EVIDENCE_REF_DROPPED)
            // are telemetry from EvidenceReferenceScrubber. They must not count as
            // consistency "unsupported" items or force MANUAL_REVIEW.
            if (u.contains("UNSUPPORTED")
                    || u.contains("MISSING_EVIDENCE")) {
                n++;
            }
        }
        return n;
    }
}
