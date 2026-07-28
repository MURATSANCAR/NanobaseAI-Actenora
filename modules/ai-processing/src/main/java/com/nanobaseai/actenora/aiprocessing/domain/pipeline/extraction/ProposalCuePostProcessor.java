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
import java.util.regex.Pattern;

/**
 * Recovers explicit proposal cues that landed in the wrong extraction bucket,
 * and seeds proposals from transcript segments when the model dropped them.
 *
 * <p>Meta-only filler ("Bu öneriyi not ediyorum ama henüz karar değil.") is neither
 * seeded nor promoted — only cues with substantive proposal content survive.
 */
public final class ProposalCuePostProcessor {

    private static final double SEEDED_CONFIDENCE = 0.92d;

    /** Known scaffolding stripped when judging substance / near-dupe keys. */
    private static final Pattern META_SCAFFOLD = Pattern.compile(
            "(?iu)(bu\\s+)?öneriyi\\s+not\\s+ediyorum(\\s+ama)?|hen[uü]z\\s+karar\\s+de[gğ]il"
    );

    /** Concrete proposal verbs / content markers (narrow — avoid dropping real notes). */
    private static final Pattern SUBSTANTIVE_CUE = Pattern.compile(
            "(?iu)(?<!\\p{L})(önerim|öneriyorum|spike|yapal[ıi]m|yapmam[ıi]z|deneyelim|"
                    + "de[gğ]erlendirmeye\\s+alal[ıi]m|erteleyelim|ba[sş]latal[ıi]m|"
                    + "dene(yelim|mek)|incele(yelim|mek)|prototip)(?!\\p{L})"
    );

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
            if (shouldBecomeProposal(proposal.text())) {
                putProposal(proposals, proposal);
            }
        }

        List<DecisionCandidate> decisions = new ArrayList<>();
        for (DecisionCandidate decision : bundle.decisions()) {
            if (shouldBecomeProposal(decision.text()) && !matcher.hasExplicitDecisionCue(decision.text())) {
                putProposal(proposals, new ProposalCandidate(
                        decision.text(), decision.evidenceSegmentIds(), decision.confidence()));
            } else if (isDroppableMetaProposalCue(decision.text())) {
                // Meta-only cue must not remain as a fake decision.
            } else {
                decisions.add(decision);
            }
        }

        List<ImportantFactCandidate> facts = new ArrayList<>();
        for (ImportantFactCandidate fact : bundle.importantFacts()) {
            if (shouldBecomeProposal(fact.text())) {
                putProposal(proposals, new ProposalCandidate(
                        fact.text(), fact.evidenceSegmentIds(), fact.confidence()));
            } else if (isDroppableMetaProposalCue(fact.text())) {
                // drop
            } else {
                facts.add(fact);
            }
        }

        List<TopicCandidate> topics = new ArrayList<>();
        for (TopicCandidate topic : bundle.topics()) {
            if (shouldBecomeProposal(topic.text())) {
                putProposal(proposals, new ProposalCandidate(
                        topic.text(), topic.evidenceSegmentIds(), topic.confidence()));
            } else if (isDroppableMetaProposalCue(topic.text())) {
                // drop
            } else {
                topics.add(topic);
            }
        }

        List<OpenQuestionCandidate> questions = new ArrayList<>();
        for (OpenQuestionCandidate question : bundle.openQuestions()) {
            if (shouldBecomeProposal(question.text())) {
                putProposal(proposals, new ProposalCandidate(
                        question.text(), question.evidenceSegmentIds(), question.confidence()));
            } else if (isDroppableMetaProposalCue(question.text())) {
                // drop
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
            putProposal(proposals, new ProposalCandidate(
                    text, List.of(segment.segmentId()), SEEDED_CONFIDENCE));
            coveredEvidence.add(segment.segmentId());
        }
    }

    private boolean isSeedableProposal(String text) {
        if (!matcher.hasProposalCue(text) || matcher.hasExplicitDecisionCue(text)) {
            return false;
        }
        if (!hasSubstantiveProposalContent(text)) {
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
        if (!hasSubstantiveProposalContent(text)) {
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

    private boolean isDroppableMetaProposalCue(String text) {
        return text != null
                && matcher.hasProposalCue(text)
                && !matcher.hasExplicitDecisionCue(text)
                && !hasSubstantiveProposalContent(text);
    }

    static boolean hasSubstantiveProposalContent(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (SUBSTANTIVE_CUE.matcher(text).find()) {
            return true;
        }
        String remainder = META_SCAFFOLD.matcher(text).replaceAll(" ");
        remainder = remainder.replaceAll("[\\p{Punct}]+", " ").replaceAll("\\s+", " ").trim();
        return remainder.length() >= 12;
    }

    private static void putProposal(Map<String, ProposalCandidate> proposals, ProposalCandidate proposal) {
        String key = dedupeKey(proposal.text());
        if (key.isBlank()) {
            return;
        }
        ProposalCandidate prior = proposals.get(key);
        if (prior == null) {
            proposals.put(key, proposal);
            return;
        }
        LinkedHashSet<String> evidence = new LinkedHashSet<>(prior.evidenceSegmentIds());
        evidence.addAll(proposal.evidenceSegmentIds());
        String keepText = preferSubstantiveText(prior.text(), proposal.text());
        proposals.put(key, new ProposalCandidate(
                keepText,
                List.copyOf(evidence),
                Math.max(prior.confidence(), proposal.confidence())
        ));
    }

    private static String preferSubstantiveText(String a, String b) {
        int scoreA = substantiveScore(a);
        int scoreB = substantiveScore(b);
        if (scoreB > scoreA) {
            return b;
        }
        if (scoreA > scoreB) {
            return a;
        }
        return a.length() >= b.length() ? a : b;
    }

    private static int substantiveScore(String text) {
        int score = 0;
        if (SUBSTANTIVE_CUE.matcher(text).find()) {
            score += 100;
        }
        String remainder = META_SCAFFOLD.matcher(text).replaceAll(" ");
        remainder = remainder.replaceAll("[\\p{Punct}]+", " ").replaceAll("\\s+", " ").trim();
        score += remainder.length();
        return score;
    }

    /** Near-dupe key: strip meta scaffolding so repeated filler collapses. */
    static String dedupeKey(String text) {
        String stripped = META_SCAFFOLD.matcher(norm(text)).replaceAll(" ");
        return stripped.replaceAll("[\\p{Punct}]+", " ").replaceAll("\\s+", " ").trim();
    }

    private static String norm(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
