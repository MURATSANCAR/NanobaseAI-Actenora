package com.nanobaseai.actenora.aiprocessing.domain.pipeline.extraction;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ProposalCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TopicCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact.DeterministicSpeechActMatcher;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact.HybridSpeechActClassifier;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact.MeetingSpeechAct;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact.SpeechActResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Recovers explicit proposal cues that landed in the wrong extraction bucket,
 * and seeds proposals from transcript segments when the model dropped them.
 */
public final class ProposalCuePostProcessor {

    private static final double SEEDED_CONFIDENCE = 0.92d;

    private final HybridSpeechActClassifier classifier;
    private final DeterministicSpeechActMatcher matcher;

    public ProposalCuePostProcessor(HybridSpeechActClassifier classifier) {
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.matcher = classifier.deterministic();
    }

    public static ProposalCuePostProcessor productionDefaults() {
        return new ProposalCuePostProcessor(HybridSpeechActClassifier.productionDefaults());
    }

    public ExtractionBundle process(ExtractionBundle bundle) {
        return process(bundle, List.of());
    }

    public ExtractionBundle process(ExtractionBundle bundle, List<SegmentInput> segments) {
        Objects.requireNonNull(bundle, "bundle");
        Map<String, ProposalCandidate> proposals = new LinkedHashMap<>();
        for (ProposalCandidate proposal : bundle.proposals()) {
            putProposal(proposals, proposal);
        }

        List<DecisionCandidate> decisions = new ArrayList<>();
        for (DecisionCandidate decision : bundle.decisions()) {
            if (shouldBecomeProposal(decision.text()) && !matcher.hasExplicitDecisionCue(decision.text())) {
                putProposal(proposals, new ProposalCandidate(
                        decision.text(), decision.evidenceSegmentIds(), decision.confidence()));
            } else {
                decisions.add(decision);
            }
        }

        List<ImportantFactCandidate> facts = new ArrayList<>();
        for (ImportantFactCandidate fact : bundle.importantFacts()) {
            if (shouldBecomeProposal(fact.text())) {
                putProposal(proposals, new ProposalCandidate(
                        fact.text(), fact.evidenceSegmentIds(), fact.confidence()));
            } else {
                facts.add(fact);
            }
        }

        List<TopicCandidate> topics = new ArrayList<>();
        for (TopicCandidate topic : bundle.topics()) {
            if (shouldBecomeProposal(topic.text())) {
                putProposal(proposals, new ProposalCandidate(
                        topic.text(), topic.evidenceSegmentIds(), topic.confidence()));
            } else {
                topics.add(topic);
            }
        }

        List<OpenQuestionCandidate> questions = new ArrayList<>();
        for (OpenQuestionCandidate question : bundle.openQuestions()) {
            if (shouldBecomeProposal(question.text())) {
                putProposal(proposals, new ProposalCandidate(
                        question.text(), question.evidenceSegmentIds(), question.confidence()));
            } else {
                questions.add(question);
            }
        }

        seedFromSegments(proposals, segments == null ? List.of() : segments);

        return new ExtractionBundle(
                topics,
                decisions,
                bundle.actionItems(),
                bundle.risks(),
                questions,
                bundle.commitments(),
                bundle.issues(),
                new ArrayList<>(proposals.values()),
                facts,
                bundle.qualityFlags(),
                bundle.evidenceSegmentIds(),
                bundle.confidence()
        );
    }

    private void seedFromSegments(Map<String, ProposalCandidate> proposals, List<SegmentInput> segments) {
        Set<String> coveredEvidence = new LinkedHashSet<>();
        for (ProposalCandidate existing : proposals.values()) {
            coveredEvidence.addAll(existing.evidenceSegmentIds());
        }
        for (SegmentInput segment : segments) {
            if (segment == null || segment.content() == null || segment.content().isBlank()) {
                continue;
            }
            if (coveredEvidence.contains(segment.segmentId())) {
                continue;
            }
            String text = segment.content().strip();
            if (!isSeedableProposal(text)) {
                continue;
            }
            // Keep one proposal per cue segment so repeated "henüz karar değil" lines survive.
            String key = "seed|" + segment.segmentId();
            proposals.putIfAbsent(key, new ProposalCandidate(
                    text, List.of(segment.segmentId()), SEEDED_CONFIDENCE));
            coveredEvidence.add(segment.segmentId());
        }
    }

    private boolean isSeedableProposal(String text) {
        if (!matcher.hasProposalCue(text) || matcher.hasExplicitDecisionCue(text)) {
            return false;
        }
        SpeechActResult act = classifier.classify(text);
        return act.speechAct() != MeetingSpeechAct.DISCUSSION_PROMPT
                && act.speechAct() != MeetingSpeechAct.STATUS_QUO
                && act.speechAct() != MeetingSpeechAct.CLOSING_META
                && act.speechAct() != MeetingSpeechAct.NOTE_INSTRUCTION;
    }

    private boolean shouldBecomeProposal(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        SpeechActResult act = classifier.classify(text);
        if (act.speechAct() == MeetingSpeechAct.DISCUSSION_PROMPT
                || act.speechAct() == MeetingSpeechAct.STATUS_QUO
                || act.speechAct() == MeetingSpeechAct.CLOSING_META
                || act.speechAct() == MeetingSpeechAct.NOTE_INSTRUCTION) {
            return false;
        }
        return matcher.hasProposalCue(text) && !matcher.hasExplicitDecisionCue(text);
    }

    private static void putProposal(Map<String, ProposalCandidate> proposals, ProposalCandidate proposal) {
        String key = norm(proposal.text());
        ProposalCandidate prior = proposals.get(key);
        if (prior == null) {
            proposals.put(key, proposal);
            return;
        }
        LinkedHashSet<String> evidence = new LinkedHashSet<>(prior.evidenceSegmentIds());
        evidence.addAll(proposal.evidenceSegmentIds());
        proposals.put(key, new ProposalCandidate(
                prior.text(),
                List.copyOf(evidence),
                Math.max(prior.confidence(), proposal.confidence())
        ));
    }

    private static String norm(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
