package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Merges per-chunk extraction candidates into a single bundle (deterministic).
 */
public final class ExtractionMerger {

    public ExtractionBundle merge(List<ExtractionBundle> chunks) {
        Objects.requireNonNull(chunks, "chunks");
        if (chunks.isEmpty()) {
            return ExtractionBundle.empty();
        }

        Map<String, TopicCandidate> topics = new LinkedHashMap<>();
        Map<String, DecisionCandidate> decisions = new LinkedHashMap<>();
        Map<String, ActionItemCandidate> actions = new LinkedHashMap<>();
        Map<String, RiskCandidate> risks = new LinkedHashMap<>();
        Map<String, OpenQuestionCandidate> questions = new LinkedHashMap<>();
        Map<String, CommitmentCandidate> commitments = new LinkedHashMap<>();
        Map<String, IssueCandidate> issues = new LinkedHashMap<>();
        Map<String, ProposalCandidate> proposals = new LinkedHashMap<>();
        Map<String, ImportantFactCandidate> facts = new LinkedHashMap<>();
        Set<String> qualityFlags = new LinkedHashSet<>();
        Set<String> evidence = new LinkedHashSet<>();
        double confidenceSum = 0.0d;

        for (ExtractionBundle chunk : chunks) {
            for (TopicCandidate topic : chunk.topics()) {
                topics.putIfAbsent(norm(topic.text()), topic);
            }
            for (DecisionCandidate decision : chunk.decisions()) {
                decisions.putIfAbsent(norm(decision.text()), decision);
            }
            for (ActionItemCandidate item : chunk.actionItems()) {
                actions.putIfAbsent(norm(item.text()), item);
            }
            for (RiskCandidate risk : chunk.risks()) {
                risks.merge(norm(risk.text()), risk, ExtractionMerger::preferRicherRisk);
            }
            for (OpenQuestionCandidate question : chunk.openQuestions()) {
                questions.putIfAbsent(norm(question.text()), question);
            }
            for (CommitmentCandidate commitment : chunk.commitments()) {
                commitments.putIfAbsent(norm(commitment.text()), commitment);
            }
            for (IssueCandidate issue : chunk.issues()) {
                issues.putIfAbsent(norm(issue.text()), issue);
            }
            for (ProposalCandidate proposal : chunk.proposals()) {
                proposals.putIfAbsent(norm(proposal.text()), proposal);
            }
            for (ImportantFactCandidate fact : chunk.importantFacts()) {
                facts.putIfAbsent(norm(fact.text()), fact);
            }
            qualityFlags.addAll(chunk.qualityFlags());
            evidence.addAll(chunk.evidenceSegmentIds());
            confidenceSum += chunk.confidence();
        }

        double confidence = confidenceSum / chunks.size();
        return MeetingNoisePatterns.stripStatusQuoDecisions(new ExtractionBundle(
                new ArrayList<>(topics.values()),
                new ArrayList<>(decisions.values()),
                new ArrayList<>(actions.values()),
                new ArrayList<>(risks.values()),
                new ArrayList<>(questions.values()),
                new ArrayList<>(commitments.values()),
                new ArrayList<>(issues.values()),
                new ArrayList<>(proposals.values()),
                new ArrayList<>(facts.values()),
                new ArrayList<>(qualityFlags),
                new ArrayList<>(evidence),
                clamp(confidence)
        ));
    }

    private static RiskCandidate preferRicherRisk(RiskCandidate a, RiskCandidate b) {
        int scoreA = (a.mitigation() != null && !a.mitigation().isBlank() ? 2 : 0)
                + (a.likelihood() != null && !a.likelihood().isBlank() ? 1 : 0);
        int scoreB = (b.mitigation() != null && !b.mitigation().isBlank() ? 2 : 0)
                + (b.likelihood() != null && !b.likelihood().isBlank() ? 1 : 0);
        return scoreB > scoreA ? b : a;
    }

    private static String norm(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static double clamp(double value) {
        if (value < 0.0d) {
            return 0.0d;
        }
        if (value > 1.0d) {
            return 1.0d;
        }
        return value;
    }
}
