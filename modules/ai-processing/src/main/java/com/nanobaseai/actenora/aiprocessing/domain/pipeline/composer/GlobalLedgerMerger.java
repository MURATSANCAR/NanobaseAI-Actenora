package com.nanobaseai.actenora.aiprocessing.domain.pipeline.composer;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ProposalCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.RiskCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency.ItemTextViews;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.consistency.SemanticCore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Grounded UNION of chunk ledger and independently grounded composer candidates,
 * then semantic dedupe. Never intersection.
 */
public final class GlobalLedgerMerger {

    public ExtractionBundle unionAndDedupe(
            ExtractionBundle groundedLedger,
            List<GlobalComposition.GlobalCandidate> composerAccepted
    ) {
        Objects.requireNonNull(groundedLedger, "groundedLedger");
        List<GlobalComposition.GlobalCandidate> accepted =
                composerAccepted == null ? List.of() : composerAccepted;

        List<DecisionCandidate> decisions = new ArrayList<>(groundedLedger.decisions());
        List<ActionItemCandidate> actions = new ArrayList<>(groundedLedger.actionItems());
        List<CommitmentCandidate> commitments = new ArrayList<>(groundedLedger.commitments());
        List<RiskCandidate> risks = new ArrayList<>(groundedLedger.risks());
        List<OpenQuestionCandidate> questions = new ArrayList<>(groundedLedger.openQuestions());
        List<ImportantFactCandidate> facts = new ArrayList<>(groundedLedger.importantFacts());
        List<ProposalCandidate> proposals = new ArrayList<>(groundedLedger.proposals());
        Set<String> evidence = new LinkedHashSet<>(groundedLedger.evidenceSegmentIds());
        List<String> flags = new ArrayList<>(groundedLedger.qualityFlags());
        if (!accepted.isEmpty()) {
            flags.add("COMPOSER_UNION_APPLIED");
        }

        for (GlobalComposition.GlobalCandidate c : accepted) {
            evidence.addAll(c.evidenceSegmentIds());
            switch (c.type()) {
                case DECISION -> {
                    if (!hasSimilar(decisions, DecisionCandidate::text, c.text())) {
                        decisions.add(new DecisionCandidate(c.text(), c.evidenceSegmentIds(), clamp(c.confidence())));
                    }
                }
                case ACTION -> {
                    if (!hasSimilar(actions, ActionItemCandidate::text, c.text())) {
                        actions.add(new ActionItemCandidate(
                                c.text(),
                                c.ownerCandidate(),
                                c.dueDateNormalized(),
                                c.evidenceSegmentIds(),
                                clamp(c.confidence()),
                                null,
                                null,
                                c.dueDateText(),
                                null
                        ));
                    }
                }
                case COMMITMENT -> {
                    if (!hasSimilar(commitments, CommitmentCandidate::text, c.text())) {
                        commitments.add(new CommitmentCandidate(
                                c.text(), c.ownerCandidate(), c.evidenceSegmentIds(), clamp(c.confidence())));
                    }
                }
                case RISK -> {
                    RiskCandidate existing = findSimilar(risks, RiskCandidate::text, c.text());
                    if (existing == null) {
                        risks.add(new RiskCandidate(
                                c.text(),
                                c.evidenceSegmentIds(),
                                clamp(c.confidence()),
                                null,
                                c.mitigation()
                        ));
                    } else if ((existing.mitigation() == null || existing.mitigation().isBlank())
                            && c.mitigation() != null && !c.mitigation().isBlank()) {
                        int idx = risks.indexOf(existing);
                        if (idx >= 0) {
                            risks.set(idx, new RiskCandidate(
                                    existing.text(),
                                    existing.evidenceSegmentIds(),
                                    Math.max(existing.confidence(), clamp(c.confidence())),
                                    existing.likelihood(),
                                    c.mitigation()
                            ));
                        }
                    }
                }
                case OPEN_QUESTION -> {
                    if (!hasSimilar(questions, OpenQuestionCandidate::text, c.text())) {
                        questions.add(new OpenQuestionCandidate(
                                c.text(), c.evidenceSegmentIds(), clamp(c.confidence())));
                    }
                }
                case IMPORTANT_FACT -> {
                    if (!hasSimilar(facts, ImportantFactCandidate::text, c.text())) {
                        facts.add(new ImportantFactCandidate(
                                c.text(), c.evidenceSegmentIds(), clamp(c.confidence())));
                    }
                }
                case PROPOSAL -> {
                    if (!hasSimilar(proposals, ProposalCandidate::text, c.text())) {
                        proposals.add(new ProposalCandidate(
                                c.text(), c.evidenceSegmentIds(), clamp(c.confidence())));
                    }
                }
            }
        }

        return new ExtractionBundle(
                groundedLedger.topics(),
                List.copyOf(decisions),
                List.copyOf(actions),
                List.copyOf(risks),
                List.copyOf(questions),
                List.copyOf(commitments),
                groundedLedger.issues(),
                List.copyOf(proposals),
                List.copyOf(facts),
                List.copyOf(flags),
                List.copyOf(evidence),
                Math.max(groundedLedger.confidence(), 0.75d)
        );
    }

    public FinalNoteDraft toDraft(ExtractionBundle bundle, String summarySeed, boolean manualReview) {
        Objects.requireNonNull(bundle, "bundle");
        return new FinalNoteDraft(
                summarySeed == null ? "" : summarySeed,
                bundle.decisions(),
                bundle.actionItems(),
                bundle.risks(),
                bundle.openQuestions(),
                bundle.commitments(),
                bundle.topics(),
                bundle.issues(),
                bundle.proposals(),
                bundle.importantFacts(),
                bundle.qualityFlags(),
                bundle.evidenceSegmentIds(),
                bundle.confidence(),
                manualReview
        );
    }

    private static <T> boolean hasSimilar(List<T> existing, Function<T, String> textOf, String candidate) {
        return findSimilar(existing, textOf, candidate) != null;
    }

    private static <T> T findSimilar(List<T> existing, Function<T, String> textOf, String candidate) {
        SemanticCore cCore = SemanticCore.extract(ItemTextViews.comparisonCore(candidate));
        for (T item : existing) {
            SemanticCore eCore = SemanticCore.extract(ItemTextViews.comparisonCore(textOf.apply(item)));
            if (eCore.topicSimilarity(cCore) >= 0.78d && eCore.actionSimilarity(cCore) >= 0.45d) {
                return item;
            }
        }
        return null;
    }

    private static double clamp(double confidence) {
        if (confidence < 0.0d) {
            return 0.0d;
        }
        if (confidence > 1.0d) {
            return 1.0d;
        }
        return confidence;
    }
}
