package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteAssembler;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.IssueCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ProposalCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.RiskCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TopicCandidate;
import com.nanobaseai.actenora.meetingintelligence.api.dto.ActionItemCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.AiCandidateBundle;
import com.nanobaseai.actenora.meetingintelligence.api.dto.CommitmentCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.DecisionCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.ImportantFactCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.IssueCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.OpenQuestionCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.ProposalCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.RiskCandidateInput;
import com.nanobaseai.actenora.meetingintelligence.api.dto.TopicCandidateInput;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Maps AI Processing {@link FinalNoteDraft} into Meeting Intelligence candidate DTOs.
 * Lives in the composition root so AI Processing does not depend on Meeting Intelligence.
 */
public final class FinalNoteDraftMapper {

    private FinalNoteDraftMapper() {
    }

    public static AiCandidateBundle toBundle(FinalNoteDraft draft) {
        Objects.requireNonNull(draft, "draft");
        List<String> qualityFlags = new ArrayList<>(draft.qualityFlags());
        if (draft.requiresManualReview() && !qualityFlags.contains("REQUIRES_MANUAL_REVIEW")) {
            qualityFlags.add("REQUIRES_MANUAL_REVIEW");
        }
        return new AiCandidateBundle(
                draft.executiveSummary(),
                draft.decisions().stream().map(FinalNoteDraftMapper::decision).toList(),
                draft.actionItems().stream().map(FinalNoteDraftMapper::actionItem).toList(),
                draft.risks().stream().map(FinalNoteDraftMapper::risk).toList(),
                draft.openQuestions().stream().map(FinalNoteDraftMapper::openQuestion).toList(),
                draft.commitments().stream().map(FinalNoteDraftMapper::commitment).toList(),
                draft.issues().stream().map(FinalNoteDraftMapper::issue).toList(),
                draft.proposals().stream().map(FinalNoteDraftMapper::proposal).toList(),
                importantFacts(draft),
                topics(draft),
                qualityFlags,
                draft.evidenceSegmentIds(),
                clamp(draft.confidence())
        );
    }

    /** Important facts only — discussed topics are now a first-class entity (see {@link #topics}). */
    private static List<ImportantFactCandidateInput> importantFacts(FinalNoteDraft draft) {
        List<ImportantFactCandidateInput> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ImportantFactCandidate fact : draft.importantFacts()) {
            String key = normalizeFactKey(fact.text());
            if (key.isEmpty() || !seen.add(key)) {
                continue;
            }
            out.add(importantFact(fact));
        }
        return List.copyOf(out);
    }

    /**
     * Usable discussed topics, mapped to first-class topic candidates so the portal renders them
     * under "GÖRÜŞÜLEN KONULAR". Previously these were folded into importantFacts (ÖNEMLİ BULGULAR).
     */
    private static List<TopicCandidateInput> topics(FinalNoteDraft draft) {
        List<TopicCandidateInput> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (TopicCandidate topic : draft.topics()) {
            if (!FinalNoteAssembler.isUsableTopic(topic)) {
                continue;
            }
            String key = normalizeFactKey(topic.text());
            if (key.isEmpty() || !seen.add(key)) {
                continue;
            }
            out.add(new TopicCandidateInput(
                    topic.text().strip(), topic.evidenceSegmentIds(), clamp(topic.confidence())));
        }
        return List.copyOf(out);
    }

    private static String normalizeFactKey(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static DecisionCandidateInput decision(DecisionCandidate candidate) {
        return new DecisionCandidateInput(
                candidate.text(),
                candidate.evidenceSegmentIds(),
                clamp(candidate.confidence()),
                candidate.rationale(),
                candidate.status()
        );
    }

    private static ActionItemCandidateInput actionItem(ActionItemCandidate candidate) {
        return new ActionItemCandidateInput(
                candidate.text(),
                candidate.owner(),
                candidate.dueDate(),
                candidate.evidenceSegmentIds(),
                clamp(candidate.confidence()),
                candidate.ownerType(),
                candidate.priority(),
                candidate.relativeDate(),
                candidate.dueAt()
        );
    }

    private static RiskCandidateInput risk(RiskCandidate candidate) {
        return new RiskCandidateInput(
                candidate.text(),
                candidate.evidenceSegmentIds(),
                clamp(candidate.confidence()),
                candidate.likelihood(),
                candidate.mitigation()
        );
    }

    private static OpenQuestionCandidateInput openQuestion(OpenQuestionCandidate candidate) {
        return new OpenQuestionCandidateInput(
                candidate.text(), candidate.evidenceSegmentIds(), clamp(candidate.confidence()));
    }

    private static CommitmentCandidateInput commitment(CommitmentCandidate candidate) {
        return new CommitmentCandidateInput(
                candidate.text(),
                candidate.owner(),
                candidate.evidenceSegmentIds(),
                clamp(candidate.confidence())
        );
    }

    private static IssueCandidateInput issue(IssueCandidate candidate) {
        return new IssueCandidateInput(candidate.text(), candidate.evidenceSegmentIds(), clamp(candidate.confidence()));
    }

    private static ProposalCandidateInput proposal(ProposalCandidate candidate) {
        return new ProposalCandidateInput(
                candidate.text(), candidate.evidenceSegmentIds(), clamp(candidate.confidence()));
    }

    private static ImportantFactCandidateInput importantFact(ImportantFactCandidate candidate) {
        return new ImportantFactCandidateInput(
                candidate.text(), candidate.evidenceSegmentIds(), clamp(candidate.confidence()));
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
