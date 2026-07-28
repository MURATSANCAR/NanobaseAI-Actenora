package com.nanobaseai.actenora.aiprocessing.domain.pipeline.filter;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ExtractionBundle;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ProposalCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TopicCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact.HybridSpeechActClassifier;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.speechact.SpeechActResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Post-merge scrub that stops type-laundered filler from reaching the final note.
 */
public final class CrossTypeMeetingItemScrubber {

    public static final String TYPE_LAUNDER_DROPPED = "TYPE_LAUNDER_DROPPED";

    private final HybridSpeechActClassifier classifier;
    private final MeetingItemPolicy policy;

    public CrossTypeMeetingItemScrubber(HybridSpeechActClassifier classifier, MeetingItemPolicy policy) {
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public static CrossTypeMeetingItemScrubber productionDefaults() {
        return new CrossTypeMeetingItemScrubber(
                HybridSpeechActClassifier.productionDefaults(),
                new MeetingItemPolicy()
        );
    }

    public ExtractionBundle scrub(ExtractionBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        Set<String> flags = new LinkedHashSet<>(bundle.qualityFlags());
        boolean dropped = false;

        List<DecisionCandidate> decisions = new ArrayList<>();
        for (DecisionCandidate item : bundle.decisions()) {
            if (keep(MeetingItemType.DECISION, item.text())) {
                decisions.add(item);
            } else {
                dropped = true;
            }
        }

        List<ActionItemCandidate> actions = new ArrayList<>();
        for (ActionItemCandidate item : bundle.actionItems()) {
            if (keep(MeetingItemType.ACTION, item.text())) {
                actions.add(item);
            } else {
                dropped = true;
            }
        }

        List<CommitmentCandidate> commitments = new ArrayList<>();
        for (CommitmentCandidate item : bundle.commitments()) {
            if (keep(MeetingItemType.COMMITMENT, item.text())) {
                commitments.add(item);
            } else {
                dropped = true;
            }
        }

        List<OpenQuestionCandidate> questions = new ArrayList<>();
        for (OpenQuestionCandidate item : bundle.openQuestions()) {
            if (keep(MeetingItemType.OPEN_QUESTION, item.text())) {
                questions.add(item);
            } else {
                dropped = true;
            }
        }

        List<TopicCandidate> topics = new ArrayList<>();
        for (TopicCandidate item : bundle.topics()) {
            if (keep(MeetingItemType.TOPIC, item.text())) {
                topics.add(item);
            } else {
                dropped = true;
            }
        }

        List<ImportantFactCandidate> facts = new ArrayList<>();
        for (ImportantFactCandidate item : bundle.importantFacts()) {
            if (keep(MeetingItemType.IMPORTANT_FACT, item.text())) {
                facts.add(item);
            } else {
                dropped = true;
            }
        }

        List<ProposalCandidate> proposals = new ArrayList<>();
        for (ProposalCandidate item : bundle.proposals()) {
            if (keep(MeetingItemType.PROPOSAL, item.text())) {
                proposals.add(item);
            } else {
                dropped = true;
            }
        }

        if (dropped) {
            flags.add(TYPE_LAUNDER_DROPPED);
        }

        return new ExtractionBundle(
                topics,
                decisions,
                actions,
                bundle.risks(),
                questions,
                commitments,
                bundle.issues(),
                proposals,
                facts,
                new ArrayList<>(flags),
                bundle.evidenceSegmentIds(),
                bundle.confidence()
        );
    }

    private boolean keep(MeetingItemType type, String text) {
        SpeechActResult act = classifier.classify(text);
        return policy.decide(type, act, text) != PolicyAction.DROP;
    }
}
