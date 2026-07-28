package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Scrubs evidence segment IDs before hard validation.
 * Unknown / ambiguous refs are dropped; items left without evidence are soft-dropped.
 * One bad evidence reference must never invalidate the whole bundle.
 */
public final class EvidenceReferenceScrubber {

    public record Outcome(
            ExtractionBundle bundle,
            int droppedRefs,
            int correctedRefs,
            int droppedItems
    ) {
    }

    private final EvidenceNearMissConfig nearMiss;

    public EvidenceReferenceScrubber() {
        this(EvidenceNearMissConfig.disabled());
    }

    public EvidenceReferenceScrubber(EvidenceNearMissConfig nearMiss) {
        this.nearMiss = Objects.requireNonNull(nearMiss, "nearMiss");
    }

    public Outcome scrub(ExtractionBundle bundle, Set<String> allowed) {
        Objects.requireNonNull(bundle, "bundle");
        Objects.requireNonNull(allowed, "allowed");

        List<String> flags = new ArrayList<>(bundle.qualityFlags());
        int droppedRefs = 0;
        int correctedRefs = 0;
        int droppedItems = 0;

        List<TopicCandidate> topics = new ArrayList<>();
        for (TopicCandidate topic : bundle.topics()) {
            RefResult refs = resolveRefs(topic.evidenceSegmentIds(), allowed);
            droppedRefs += refs.dropped();
            correctedRefs += refs.corrected();
            if (refs.ids().isEmpty()) {
                droppedItems++;
                addFlag(flags, "EVIDENCE_ITEM_SOFT_DROPPED");
            } else {
                topics.add(new TopicCandidate(topic.text(), topic.summary(), refs.ids(), topic.confidence()));
            }
        }

        List<DecisionCandidate> decisions = new ArrayList<>();
        for (DecisionCandidate decision : bundle.decisions()) {
            RefResult refs = resolveRefs(decision.evidenceSegmentIds(), allowed);
            droppedRefs += refs.dropped();
            correctedRefs += refs.corrected();
            if (refs.ids().isEmpty()) {
                droppedItems++;
                addFlag(flags, "EVIDENCE_ITEM_SOFT_DROPPED");
            } else {
                decisions.add(new DecisionCandidate(
                        decision.text(), refs.ids(), decision.confidence(),
                        decision.rationale(), decision.status()));
            }
        }

        List<ActionItemCandidate> actions = new ArrayList<>();
        for (ActionItemCandidate item : bundle.actionItems()) {
            RefResult refs = resolveRefs(item.evidenceSegmentIds(), allowed);
            droppedRefs += refs.dropped();
            correctedRefs += refs.corrected();
            if (refs.ids().isEmpty()) {
                droppedItems++;
                addFlag(flags, "EVIDENCE_ITEM_SOFT_DROPPED");
            } else {
                actions.add(new ActionItemCandidate(
                        item.text(), item.owner(), item.dueDate(), refs.ids(), item.confidence(),
                        item.ownerType(), item.priority(), item.relativeDate()));
            }
        }

        List<RiskCandidate> risks = new ArrayList<>();
        for (RiskCandidate risk : bundle.risks()) {
            RefResult refs = resolveRefs(risk.evidenceSegmentIds(), allowed);
            droppedRefs += refs.dropped();
            correctedRefs += refs.corrected();
            if (refs.ids().isEmpty()) {
                droppedItems++;
                addFlag(flags, "EVIDENCE_ITEM_SOFT_DROPPED");
            } else {
                risks.add(new RiskCandidate(
                        risk.text(), refs.ids(), risk.confidence(), risk.likelihood(), risk.mitigation()));
            }
        }

        List<OpenQuestionCandidate> questions = new ArrayList<>();
        for (OpenQuestionCandidate question : bundle.openQuestions()) {
            RefResult refs = resolveRefs(question.evidenceSegmentIds(), allowed);
            droppedRefs += refs.dropped();
            correctedRefs += refs.corrected();
            if (refs.ids().isEmpty()) {
                droppedItems++;
                addFlag(flags, "EVIDENCE_ITEM_SOFT_DROPPED");
            } else {
                questions.add(new OpenQuestionCandidate(question.text(), refs.ids(), question.confidence()));
            }
        }

        List<CommitmentCandidate> commitments = new ArrayList<>();
        for (CommitmentCandidate commitment : bundle.commitments()) {
            RefResult refs = resolveRefs(commitment.evidenceSegmentIds(), allowed);
            droppedRefs += refs.dropped();
            correctedRefs += refs.corrected();
            if (refs.ids().isEmpty()) {
                droppedItems++;
                addFlag(flags, "EVIDENCE_ITEM_SOFT_DROPPED");
            } else {
                commitments.add(new CommitmentCandidate(
                        commitment.text(), commitment.owner(), refs.ids(), commitment.confidence()));
            }
        }

        List<IssueCandidate> issues = new ArrayList<>();
        for (IssueCandidate issue : bundle.issues()) {
            RefResult refs = resolveRefs(issue.evidenceSegmentIds(), allowed);
            droppedRefs += refs.dropped();
            correctedRefs += refs.corrected();
            if (refs.ids().isEmpty()) {
                droppedItems++;
                addFlag(flags, "EVIDENCE_ITEM_SOFT_DROPPED");
            } else {
                issues.add(new IssueCandidate(issue.text(), refs.ids(), issue.confidence()));
            }
        }

        List<ProposalCandidate> proposals = new ArrayList<>();
        for (ProposalCandidate proposal : bundle.proposals()) {
            RefResult refs = resolveRefs(proposal.evidenceSegmentIds(), allowed);
            droppedRefs += refs.dropped();
            correctedRefs += refs.corrected();
            if (refs.ids().isEmpty()) {
                droppedItems++;
                addFlag(flags, "EVIDENCE_ITEM_SOFT_DROPPED");
            } else {
                proposals.add(new ProposalCandidate(proposal.text(), refs.ids(), proposal.confidence()));
            }
        }

        List<ImportantFactCandidate> facts = new ArrayList<>();
        for (ImportantFactCandidate fact : bundle.importantFacts()) {
            RefResult refs = resolveRefs(fact.evidenceSegmentIds(), allowed);
            droppedRefs += refs.dropped();
            correctedRefs += refs.corrected();
            if (refs.ids().isEmpty()) {
                droppedItems++;
                addFlag(flags, "EVIDENCE_ITEM_SOFT_DROPPED");
            } else {
                facts.add(new ImportantFactCandidate(fact.text(), refs.ids(), fact.confidence()));
            }
        }

        RefResult root = resolveRefs(bundle.evidenceSegmentIds(), allowed);
        droppedRefs += root.dropped();
        correctedRefs += root.corrected();

        if (droppedRefs > 0) {
            addFlag(flags, "EVIDENCE_REF_DROPPED");
        }
        if (correctedRefs > 0) {
            addFlag(flags, "EVIDENCE_REF_CORRECTED");
        }

        ExtractionBundle scrubbed = new ExtractionBundle(
                topics, decisions, actions, risks, questions, commitments,
                issues, proposals, facts, flags, root.ids(), bundle.confidence()
        );
        return new Outcome(scrubbed, droppedRefs, correctedRefs, droppedItems);
    }

    private RefResult resolveRefs(List<String> evidenceIds, Set<String> allowed) {
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            return new RefResult(List.of(), 0, 0);
        }
        LinkedHashSet<String> kept = new LinkedHashSet<>();
        int dropped = 0;
        int corrected = 0;
        for (String raw : evidenceIds) {
            if (raw == null || raw.isBlank()) {
                dropped++;
                continue;
            }
            String id = raw.trim();
            if (allowed.contains(id)) {
                kept.add(id);
                continue;
            }
            String prefixHit = uniquePrefixMatch(id, allowed);
            if (prefixHit != null) {
                kept.add(prefixHit);
                corrected++;
                continue;
            }
            if (nearMiss.enabled()) {
                String fuzzy = uniqueNearMiss(id, allowed);
                if (fuzzy != null) {
                    kept.add(fuzzy);
                    corrected++;
                    continue;
                }
            }
            dropped++;
        }
        return new RefResult(List.copyOf(kept), dropped, corrected);
    }

    private String uniquePrefixMatch(String modelId, Set<String> allowed) {
        if (modelId.length() < nearMiss.minPrefixLength()) {
            return null;
        }
        String match = null;
        for (String candidate : allowed) {
            if (candidate.startsWith(modelId) && candidate.length() > modelId.length()) {
                if (match != null) {
                    return null; // ambiguous
                }
                match = candidate;
            }
        }
        return match;
    }

    private String uniqueNearMiss(String modelId, Set<String> allowed) {
        String match = null;
        for (String candidate : allowed) {
            int distance = levenshtein(modelId, candidate);
            if (distance > 0 && distance <= nearMiss.maxDistance()) {
                if (match != null) {
                    return null;
                }
                match = candidate;
            }
        }
        if (match != null && nearMiss.requireUniqueCandidate()) {
            return match;
        }
        return match;
    }

    static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[m];
    }

    private static void addFlag(List<String> flags, String flag) {
        if (!flags.contains(flag)) {
            flags.add(flag);
        }
    }

    private record RefResult(List<String> ids, int dropped, int corrected) {
    }
}
