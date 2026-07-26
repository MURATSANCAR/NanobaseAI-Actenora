package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.RiskCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TopicCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.CandidateKind;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationParticipant;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationSegment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Maps AI pipeline drafts/segments into validation-engine inputs.
 * String segment ids are projected to stable UUIDs via nameUUIDFromBytes.
 */
public final class ValidationCandidateMapper {

    private ValidationCandidateMapper() {
    }

    public static List<ValidationSegment> toSegments(List<SegmentInput> segments) {
        Objects.requireNonNull(segments, "segments");
        List<ValidationSegment> mapped = new ArrayList<>();
        for (SegmentInput segment : segments) {
            mapped.add(new ValidationSegment(
                    toSegmentUuid(segment.segmentId()),
                    segment.sequence(),
                    segment.speakerDisplayName(),
                    segment.speakerDisplayName(),
                    segment.startOffsetMs(),
                    segment.endOffsetMs(),
                    segment.content(),
                    segment.markerNear()
            ));
        }
        return List.copyOf(mapped);
    }

    public static List<ValidationParticipant> participantsFromSpeakers(List<SegmentInput> segments) {
        Objects.requireNonNull(segments, "segments");
        Map<String, ValidationParticipant> byName = new LinkedHashMap<>();
        for (SegmentInput segment : segments) {
            String name = segment.speakerDisplayName();
            if (name == null || name.isBlank()) {
                continue;
            }
            String key = name.trim().toLowerCase();
            byName.computeIfAbsent(key, ignored -> new ValidationParticipant(
                    toParticipantUuid(name.trim()),
                    name.trim(),
                    null,
                    null
            ));
        }
        return List.copyOf(byName.values());
    }

    public static List<ValidationCandidate> toCandidates(FinalNoteDraft draft) {
        Objects.requireNonNull(draft, "draft");
        List<ValidationCandidate> candidates = new ArrayList<>();
        int i = 0;
        for (TopicCandidate topic : draft.topics()) {
            candidates.add(build("topic-" + (++i), CandidateKind.TOPIC, topic.text(),
                    topic.evidenceSegmentIds(), topic.confidence(), null, null, false));
        }
        for (DecisionCandidate decision : draft.decisions()) {
            candidates.add(build("decision-" + (++i), CandidateKind.DECISION, decision.text(),
                    decision.evidenceSegmentIds(), decision.confidence(), null, null, true));
        }
        for (ActionItemCandidate item : draft.actionItems()) {
            candidates.add(build("action-" + (++i), CandidateKind.ACTION_ITEM, item.text(),
                    item.evidenceSegmentIds(), item.confidence(), item.owner(), item.dueDate(), false));
        }
        for (RiskCandidate risk : draft.risks()) {
            candidates.add(build("risk-" + (++i), CandidateKind.RISK, risk.text(),
                    risk.evidenceSegmentIds(), risk.confidence(), null, null, false));
        }
        for (OpenQuestionCandidate question : draft.openQuestions()) {
            candidates.add(build("question-" + (++i), CandidateKind.OPEN_QUESTION, question.text(),
                    question.evidenceSegmentIds(), question.confidence(), null, null, false));
        }
        for (CommitmentCandidate commitment : draft.commitments()) {
            candidates.add(build("commitment-" + (++i), CandidateKind.COMMITMENT, commitment.text(),
                    commitment.evidenceSegmentIds(), commitment.confidence(), commitment.owner(), null, false));
        }
        return List.copyOf(candidates);
    }

    private static ValidationCandidate build(
            String key,
            CandidateKind kind,
            String text,
            List<String> evidence,
            double confidence,
            String owner,
            String dueDate,
            boolean markedAsDecision
    ) {
        ValidationCandidate.Builder builder = ValidationCandidate.builder(
                key,
                kind,
                text,
                BigDecimal.valueOf(clamp(confidence)).setScale(4, RoundingMode.HALF_UP)
        ).evidenceSegmentIds(evidence.stream().map(ValidationCandidateMapper::toSegmentUuid).toList())
                .markedAsDecision(markedAsDecision);
        if (owner != null && !owner.isBlank()) {
            builder.owner(toParticipantUuid(owner.trim()).toString(), owner.trim());
        }
        if (dueDate != null && !dueDate.isBlank()) {
            builder.dueDateText(dueDate.trim());
        }
        return builder.build();
    }

    public static UUID toSegmentUuid(String segmentId) {
        Objects.requireNonNull(segmentId, "segmentId");
        return UUID.nameUUIDFromBytes(("segment:" + segmentId).getBytes(StandardCharsets.UTF_8));
    }

    public static UUID toParticipantUuid(String displayName) {
        Objects.requireNonNull(displayName, "displayName");
        return UUID.nameUUIDFromBytes(("participant:" + displayName.trim().toLowerCase())
                .getBytes(StandardCharsets.UTF_8));
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
