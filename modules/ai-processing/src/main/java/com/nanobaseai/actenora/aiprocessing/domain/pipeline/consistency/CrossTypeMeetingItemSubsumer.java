package com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.action.ActionDeduplicator;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.action.ActionIdentityNormalizer;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.ItemLineageRecord;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.ItemLineageRecorder;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageOperation;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageReasonCode;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.lineage.LineageStage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Conservative cross-type subsumption: Decision ⊃ Action ⊃ Commitment when cores and
 * evidence overlap strongly (e.g. WhatsApp group fan-out).
 */
public final class CrossTypeMeetingItemSubsumer {

    public static final String DROPPED_ACTION = "CROSS_TYPE_ACTION_SUBSUMED";
    public static final String DROPPED_COMMITMENT = "CROSS_TYPE_COMMITMENT_SUBSUMED";

    public static final double CORE_JACCARD = 0.45d;

    private final ActionIdentityNormalizer identity = new ActionIdentityNormalizer();

    public record Outcome(
            List<DecisionCandidate> decisions,
            List<ActionItemCandidate> actions,
            List<CommitmentCandidate> commitments,
            List<String> flags,
            int actionsDropped,
            int commitmentsDropped
    ) {
    }

    public Outcome apply(
            List<DecisionCandidate> decisions,
            List<ActionItemCandidate> actions,
            List<CommitmentCandidate> commitments
    ) {
        Objects.requireNonNull(decisions, "decisions");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(commitments, "commitments");

        List<DecisionCandidate> keptDecisions = List.copyOf(decisions);
        Set<String> flags = new LinkedHashSet<>();
        int actionsDropped = 0;
        int commitmentsDropped = 0;

        // Collapse near-dup actions (same core/evidence/anchor).
        List<ActionItemCandidate> uniqueActions = new ArrayList<>(collapseSimilarActions(actions));
        actionsDropped += Math.max(0, actions.size() - uniqueActions.size());

        // For each decision, keep at most one action that restates the same closed selection.
        List<ActionItemCandidate> keptActions = collapseActionsAgainstDecisions(uniqueActions, keptDecisions);
        actionsDropped += Math.max(0, uniqueActions.size() - keptActions.size());
        if (actionsDropped > 0) {
            flags.add(DROPPED_ACTION);
        }

        List<CommitmentCandidate> keptCommitments = new ArrayList<>();
        for (CommitmentCandidate commitment : commitments) {
            if (subsumedByDecision(commitment.text(), commitment.evidenceSegmentIds(), keptDecisions)
                    || subsumedByAction(commitment, keptActions)
                    || sharesDecisionAnchor(commitment.text(), commitment.evidenceSegmentIds(), keptDecisions)
                    || sharesActionAnchor(commitment, keptActions)) {
                commitmentsDropped++;
                flags.add(DROPPED_COMMITMENT);
                continue;
            }
            keptCommitments.add(commitment);
        }

        Outcome outcome = new Outcome(
                keptDecisions,
                List.copyOf(keptActions),
                List.copyOf(keptCommitments),
                List.copyOf(flags),
                actionsDropped,
                commitmentsDropped
        );
        recordLineageObservability(actions, commitments, outcome);
        return outcome;
    }

    /** Side-effect only: never mutates outcome; failures are swallowed by the recorder. */
    private void recordLineageObservability(
            List<ActionItemCandidate> inputActions,
            List<CommitmentCandidate> inputCommitments,
            Outcome outcome
    ) {
        try {
            ItemLineageRecorder recorder = ItemLineageRecorder.current();
            if (!recorder.isEnabled()) {
                return;
            }
            Instant now = Instant.now();
            Set<String> keptActionKeys = new HashSet<>();
            for (ActionItemCandidate a : outcome.actions()) {
                keptActionKeys.add(identity.canonicalCore(a) + "|" + String.join(",", a.evidenceSegmentIds()));
                recorder.record(new ItemLineageRecord(
                        "action-" + Integer.toHexString(Objects.hash(a.text(), a.evidenceSegmentIds())),
                        "ACTION_ITEM",
                        LineageStage.CROSS_TYPE_RESOLUTION,
                        LineageOperation.KEEP,
                        LineageReasonCode.POLICY_KEEP,
                        List.of(),
                        ItemLineageRecord.snapshot(a.text(), a.owner(), a.relativeDate(), a.evidenceSegmentIds()),
                        ItemLineageRecord.snapshot(a.text(), a.owner(), a.relativeDate(), a.evidenceSegmentIds()),
                        "cross-type-existing-v1",
                        now,
                        null,
                        null,
                        null
                ));
            }
            for (ActionItemCandidate a : inputActions) {
                String key = identity.canonicalCore(a) + "|" + String.join(",", a.evidenceSegmentIds());
                if (keptActionKeys.contains(key)) {
                    continue;
                }
                recorder.record(new ItemLineageRecord(
                        "action-" + Integer.toHexString(Objects.hash(a.text(), a.evidenceSegmentIds())),
                        "ACTION_ITEM",
                        LineageStage.CROSS_TYPE_RESOLUTION,
                        LineageOperation.DROP,
                        LineageReasonCode.CROSS_TYPE_ACTION_SUBSUMED,
                        List.of(),
                        ItemLineageRecord.snapshot(a.text(), a.owner(), a.relativeDate(), a.evidenceSegmentIds()),
                        Map.of(),
                        "cross-type-existing-v1",
                        now,
                        null,
                        null,
                        null
                ));
            }
            Set<String> keptCommitmentKeys = new HashSet<>();
            for (CommitmentCandidate c : outcome.commitments()) {
                keptCommitmentKeys.add(identity.canonicalCore(c.text()) + "|" + String.join(",", c.evidenceSegmentIds()));
            }
            for (CommitmentCandidate c : inputCommitments) {
                String key = identity.canonicalCore(c.text()) + "|" + String.join(",", c.evidenceSegmentIds());
                if (keptCommitmentKeys.contains(key)) {
                    continue;
                }
                recorder.record(new ItemLineageRecord(
                        "commitment-" + Integer.toHexString(Objects.hash(c.text(), c.evidenceSegmentIds())),
                        "COMMITMENT",
                        LineageStage.CROSS_TYPE_RESOLUTION,
                        LineageOperation.DROP,
                        LineageReasonCode.CROSS_TYPE_COMMITMENT_SUBSUMED,
                        List.of(),
                        ItemLineageRecord.snapshot(c.text(), c.owner(), null, c.evidenceSegmentIds()),
                        Map.of(),
                        "cross-type-existing-v1",
                        now,
                        null,
                        null,
                        null
                ));
            }
        } catch (RuntimeException ignored) {
            // Observability must never fail the pipeline.
        }
    }

    public ExtractionBundle applyToBundle(ExtractionBundle bundle) {
        Outcome outcome = apply(bundle.decisions(), bundle.actionItems(), bundle.commitments());
        Set<String> flags = new LinkedHashSet<>(bundle.qualityFlags());
        flags.addAll(outcome.flags());
        return new ExtractionBundle(
                bundle.topics(),
                outcome.decisions(),
                outcome.actions(),
                bundle.risks(),
                bundle.openQuestions(),
                outcome.commitments(),
                bundle.issues(),
                bundle.proposals(),
                bundle.importantFacts(),
                List.copyOf(flags),
                bundle.evidenceSegmentIds(),
                bundle.confidence()
        );
    }

    public FinalNoteDraft applyToDraft(FinalNoteDraft draft) {
        Outcome outcome = apply(draft.decisions(), draft.actionItems(), draft.commitments());
        Set<String> flags = new LinkedHashSet<>(draft.qualityFlags());
        flags.addAll(outcome.flags());
        return new FinalNoteDraft(
                draft.executiveSummary(),
                outcome.decisions(),
                outcome.actions(),
                draft.risks(),
                draft.openQuestions(),
                outcome.commitments(),
                draft.topics(),
                draft.issues(),
                draft.proposals(),
                draft.importantFacts(),
                new ArrayList<>(flags),
                draft.evidenceSegmentIds(),
                draft.confidence(),
                draft.requiresManualReview()
        );
    }

    private List<ActionItemCandidate> collapseSimilarActions(List<ActionItemCandidate> actions) {
        if (actions.size() <= 1) {
            return List.copyOf(actions);
        }
        return new ActionDeduplicator().deduplicate(actions).actions();
    }

    private List<ActionItemCandidate> collapseActionsAgainstDecisions(
            List<ActionItemCandidate> actions,
            List<DecisionCandidate> decisions
    ) {
        if (actions.isEmpty() || decisions.isEmpty()) {
            return List.copyOf(actions);
        }
        List<ActionItemCandidate> remaining = new ArrayList<>(actions);
        for (DecisionCandidate decision : decisions) {
            List<ActionItemCandidate> cluster = new ArrayList<>();
            List<ActionItemCandidate> others = new ArrayList<>();
            for (ActionItemCandidate action : remaining) {
                if (sharesDecisionAnchor(action.text(), action.evidenceSegmentIds(), List.of(decision))) {
                    cluster.add(action);
                } else {
                    others.add(action);
                }
            }
            if (cluster.size() <= 1) {
                continue;
            }
            // Keep the most informative action in the decision cluster.
            ActionItemCandidate best = cluster.stream()
                    .max(Comparator.comparingInt(a -> a.text().length()))
                    .orElseThrow();
            others.add(best);
            remaining = others;
        }
        return List.copyOf(remaining);
    }

    private boolean subsumedByDecision(
            String text,
            List<String> evidenceIds,
            List<DecisionCandidate> decisions
    ) {
        String actionCore = identity.canonicalCore(text);
        if (actionCore.isBlank()) {
            return false;
        }
        for (DecisionCandidate decision : decisions) {
            String dCore = identity.canonicalCore(decision.text());
            if (dCore.isBlank()) {
                continue;
            }
            boolean similar = coresSimilar(actionCore, dCore);
            boolean overlap = ActionDeduplicator.evidenceOverlap(evidenceIds, decision.evidenceSegmentIds());
            if (similar && (overlap || tokenJaccard(actionCore, dCore) >= 0.60d)) {
                return true;
            }
        }
        return false;
    }

    private boolean sharesDecisionAnchor(
            String text,
            List<String> evidenceIds,
            List<DecisionCandidate> decisions
    ) {
        String core = identity.canonicalCore(text);
        for (DecisionCandidate decision : decisions) {
            if (!ActionDeduplicator.evidenceOverlap(evidenceIds, decision.evidenceSegmentIds())) {
                continue;
            }
            if (sharesDistinctiveAnchor(core, identity.canonicalCore(decision.text()))) {
                return true;
            }
        }
        return false;
    }

    private boolean sharesActionAnchor(CommitmentCandidate commitment, List<ActionItemCandidate> actions) {
        String cCore = identity.canonicalCore(commitment.text());
        for (ActionItemCandidate action : actions) {
            if (!ActionDeduplicator.evidenceOverlap(commitment.evidenceSegmentIds(), action.evidenceSegmentIds())) {
                continue;
            }
            if (sharesDistinctiveAnchor(cCore, identity.canonicalCore(action.text()))) {
                return true;
            }
        }
        return false;
    }

    private boolean subsumedByAction(CommitmentCandidate commitment, List<ActionItemCandidate> actions) {
        String cCore = identity.canonicalCore(commitment.text());
        if (cCore.isBlank()) {
            return false;
        }
        for (ActionItemCandidate action : actions) {
            String aCore = identity.canonicalCore(action.text());
            if (aCore.isBlank()) {
                continue;
            }
            boolean similar = coresSimilar(cCore, aCore);
            boolean overlap = ActionDeduplicator.evidenceOverlap(
                    commitment.evidenceSegmentIds(), action.evidenceSegmentIds());
            if (similar && (overlap || tokenJaccard(cCore, aCore) >= 0.60d)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sharesDistinctiveAnchor(String coreA, String coreB) {
        Set<String> ta = tokens(coreA);
        Set<String> tb = tokens(coreB);
        for (String a : ta) {
            if (a.length() >= 6 && tb.contains(a)) {
                return true;
            }
        }
        return false;
    }

    private static boolean coresSimilar(String a, String b) {
        if (a.equals(b) || a.contains(b) || b.contains(a)) {
            return true;
        }
        return tokenJaccard(a, b) >= CORE_JACCARD;
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
}
