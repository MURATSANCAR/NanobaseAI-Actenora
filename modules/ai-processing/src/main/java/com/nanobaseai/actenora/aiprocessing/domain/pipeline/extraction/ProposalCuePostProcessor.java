package com.nanobaseai.actenora.aiprocessing.domain.pipeline.extraction;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ProposalCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TopicCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact.DeterministicSpeechActMatcher;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact.HybridSpeechActClassifier;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact.MeetingSpeechAct;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact.SpeechActResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Recovers explicit proposal cues that landed in the wrong extraction bucket.
 */
public final class ProposalCuePostProcessor {

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
        Objects.requireNonNull(bundle, "bundle");
        Map<String, ProposalCandidate> proposals = new LinkedHashMap<>();
        for (ProposalCandidate proposal : bundle.proposals()) {
            proposals.putIfAbsent(norm(proposal.text()), proposal);
        }

        List<DecisionCandidate> decisions = new ArrayList<>();
        for (DecisionCandidate decision : bundle.decisions()) {
            if (shouldBecomeProposal(decision.text()) && !matcher.hasExplicitDecisionCue(decision.text())) {
                proposals.putIfAbsent(norm(decision.text()),
                        new ProposalCandidate(decision.text(), decision.evidenceSegmentIds(), decision.confidence()));
            } else {
                decisions.add(decision);
            }
        }

        List<ImportantFactCandidate> facts = new ArrayList<>();
        for (ImportantFactCandidate fact : bundle.importantFacts()) {
            if (shouldBecomeProposal(fact.text())) {
                proposals.putIfAbsent(norm(fact.text()),
                        new ProposalCandidate(fact.text(), fact.evidenceSegmentIds(), fact.confidence()));
            } else {
                facts.add(fact);
            }
        }

        List<TopicCandidate> topics = new ArrayList<>();
        for (TopicCandidate topic : bundle.topics()) {
            if (shouldBecomeProposal(topic.text())) {
                proposals.putIfAbsent(norm(topic.text()),
                        new ProposalCandidate(topic.text(), topic.evidenceSegmentIds(), topic.confidence()));
            } else {
                topics.add(topic);
            }
        }

        List<OpenQuestionCandidate> questions = new ArrayList<>();
        for (OpenQuestionCandidate question : bundle.openQuestions()) {
            if (shouldBecomeProposal(question.text())) {
                proposals.putIfAbsent(norm(question.text()),
                        new ProposalCandidate(question.text(), question.evidenceSegmentIds(), question.confidence()));
            } else {
                questions.add(question);
            }
        }

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

    private static String norm(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
