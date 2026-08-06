package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ActionItemCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.CommitmentCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.DecisionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.FinalNoteDraft;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ImportantFactCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.IssueCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.OpenQuestionCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.ProposalCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.RiskCandidate;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.SegmentInput;
import com.nanobaseai.actenora.aiprocessing.domain.pipeline.TopicCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.CandidateKind;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationParticipant;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationSegment;
import com.nanobaseai.actenora.sharedkernel.domain.PersonIdentityNormalizer;

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
        List<String> names = new ArrayList<>();
        for (SegmentInput segment : segments) {
            String rawName = segment.speakerDisplayName();
            if (PersonIdentityNormalizer.isGenericSpeakerLabel(rawName)) {
                continue;
            }
            String name = PersonIdentityNormalizer.displayName(rawName);
            if (PersonIdentityNormalizer.isGenericSpeakerLabel(name)) {
                continue;
            }
            String key = PersonIdentityNormalizer.identityKey(name);
            if (key.isBlank()) {
                continue;
            }
            names.add(name);
        }
        List<ValidationParticipant> participants = new ArrayList<>();
        for (String name : PersonIdentityNormalizer.canonicalRoster(names).values()) {
            participants.add(new ValidationParticipant(
                    toParticipantUuid(name),
                    name,
                    null,
                    null
            ));
        }
        return List.copyOf(participants);
    }

    /**
     * Merges transcript speakers with calendar invitees. Invitees win for email/entra when present.
     */
    public static List<ValidationParticipant> mergeParticipants(
            List<ValidationParticipant> speakers,
            List<ValidationParticipant> invitees
    ) {
        List<ValidationParticipant> all = new ArrayList<>();
        if (speakers != null) {
            all.addAll(speakers);
        }
        if (invitees != null) {
            all.addAll(invitees);
        }
        List<String> aliasInputs = new ArrayList<>();
        for (ValidationParticipant participant : all) {
            aliasInputs.add(participant.displayName());
            participant.emailOptional().ifPresent(aliasInputs::add);
        }
        List<String> canonicalNames = List.copyOf(
                PersonIdentityNormalizer.canonicalRoster(aliasInputs).values());
        Map<String, ValidationParticipant> byKey = new LinkedHashMap<>();
        for (ValidationParticipant participant : all) {
            String canonicalName = PersonIdentityNormalizer.resolveUnique(
                    participant.displayName(), canonicalNames).orElse(participant.displayName());
            mergeParticipant(byKey, new ValidationParticipant(
                    participant.participantId(),
                    canonicalName,
                    participant.email(),
                    participant.entraUserId()
            ));
        }
        return List.copyOf(byKey.values());
    }

    public static ValidationParticipant fromInvitee(
            String displayName,
            String email,
            String entraUserId
    ) {
        String name = PersonIdentityNormalizer.displayName(displayName);
        if (name.isBlank()) {
            name = PersonIdentityNormalizer.displayName(email);
        }
        if (name.isBlank()) {
            name = "participant";
        }
        return new ValidationParticipant(
                toParticipantUuid(name),
                name,
                email == null || email.isBlank() ? null : email.trim(),
                entraUserId == null || entraUserId.isBlank() ? null : entraUserId.trim()
        );
    }

    private static String participantMergeKey(ValidationParticipant participant) {
        String key = PersonIdentityNormalizer.identityKey(participant.displayName());
        if (key.isBlank() && participant.emailOptional().isPresent()) {
            key = PersonIdentityNormalizer.identityKey(participant.emailOptional().orElseThrow());
        }
        return "person:" + key;
    }

    private static void mergeParticipant(
            Map<String, ValidationParticipant> byKey,
            ValidationParticipant incoming
    ) {
        String key = participantMergeKey(incoming);
        ValidationParticipant existing = byKey.get(key);
        if (existing == null) {
            byKey.put(key, incoming);
            return;
        }
        String preferredName = PersonIdentityNormalizer.canonicalRoster(
                List.of(existing.displayName(), incoming.displayName()))
                .values().stream().findFirst().orElse(existing.displayName());
        String email = incoming.emailOptional().orElse(existing.email());
        String entraUserId = incoming.entraUserIdOptional().orElse(existing.entraUserId());
        UUID participantId = incoming.emailOptional().isPresent() || incoming.entraUserIdOptional().isPresent()
                ? incoming.participantId()
                : existing.participantId();
        byKey.put(key, new ValidationParticipant(participantId, preferredName, email, entraUserId));
    }

    public static List<ValidationCandidate> toCandidates(FinalNoteDraft draft) {
        return toCandidates(draft, null, null);
    }

    public static List<ValidationCandidate> toCandidates(
            FinalNoteDraft draft,
            String meetingStartedAtIso,
            String meetingTimezone
    ) {
        Objects.requireNonNull(draft, "draft");
        List<ValidationCandidate> candidates = new ArrayList<>();
        int i = 0;
        for (TopicCandidate topic : draft.topics()) {
            candidates.add(build("topic-" + (++i), CandidateKind.TOPIC, topic.text(),
                    topic.evidenceSegmentIds(), topic.confidence(), null, null, null, null,
                    meetingStartedAtIso, meetingTimezone, false));
        }
        for (DecisionCandidate decision : draft.decisions()) {
            candidates.add(build("decision-" + (++i), CandidateKind.DECISION, decision.text(),
                    decision.evidenceSegmentIds(), decision.confidence(), null, null, null, null,
                    meetingStartedAtIso, meetingTimezone, true));
        }
        for (ActionItemCandidate item : draft.actionItems()) {
            candidates.add(build("action-" + (++i), CandidateKind.ACTION_ITEM, item.text(),
                    item.evidenceSegmentIds(), item.confidence(), item.owner(),
                    item.dueDate(), item.relativeDate(), item.dueAt(),
                    meetingStartedAtIso, meetingTimezone, false));
        }
        for (RiskCandidate risk : draft.risks()) {
            candidates.add(build("risk-" + (++i), CandidateKind.RISK, risk.text(),
                    risk.evidenceSegmentIds(), risk.confidence(), null, null, null, null,
                    meetingStartedAtIso, meetingTimezone, false));
        }
        for (OpenQuestionCandidate question : draft.openQuestions()) {
            candidates.add(build("question-" + (++i), CandidateKind.OPEN_QUESTION, question.text(),
                    question.evidenceSegmentIds(), question.confidence(), null, null, null, null,
                    meetingStartedAtIso, meetingTimezone, false));
        }
        for (CommitmentCandidate commitment : draft.commitments()) {
            candidates.add(build("commitment-" + (++i), CandidateKind.COMMITMENT, commitment.text(),
                    commitment.evidenceSegmentIds(), commitment.confidence(), commitment.owner(), null, null, null,
                    meetingStartedAtIso, meetingTimezone, false));
        }
        for (IssueCandidate issue : draft.issues()) {
            candidates.add(build("issue-" + (++i), CandidateKind.ISSUE, issue.text(),
                    issue.evidenceSegmentIds(), issue.confidence(), null, null, null, null,
                    meetingStartedAtIso, meetingTimezone, false));
        }
        for (ProposalCandidate proposal : draft.proposals()) {
            candidates.add(build("proposal-" + (++i), CandidateKind.PROPOSAL, proposal.text(),
                    proposal.evidenceSegmentIds(), proposal.confidence(), null, null, null, null,
                    meetingStartedAtIso, meetingTimezone, false));
        }
        for (ImportantFactCandidate fact : draft.importantFacts()) {
            candidates.add(build("fact-" + (++i), CandidateKind.IMPORTANT_FACT, fact.text(),
                    fact.evidenceSegmentIds(), fact.confidence(), null, null, null, null,
                    meetingStartedAtIso, meetingTimezone, false));
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
            String relativeDate,
            String dueAt,
            String meetingStartedAtIso,
            String meetingTimezone,
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
        if (dueAt != null && !dueAt.isBlank()) {
            builder.dueAtText(dueAt.trim());
        }
        if (relativeDate != null && !relativeDate.isBlank()) {
            builder.relativeDateText(relativeDate.trim());
        }
        builder.dateResolution(
                deriveDateResolutionStatus(dueDate, relativeDate, dueAt),
                deriveDateResolutionSource(dueDate, relativeDate, dueAt),
                relativeDate != null && !relativeDate.isBlank() && dueAt != null && !dueAt.isBlank()
                        ? trimToNull(meetingStartedAtIso)
                        : null,
                relativeDate != null && !relativeDate.isBlank() && dueAt != null && !dueAt.isBlank()
                        ? trimToNull(meetingTimezone)
                        : null
        );
        return builder.build();
    }

    private static String deriveDateResolutionStatus(String dueDate, String relativeDate, String dueAt) {
        if (relativeDate != null && !relativeDate.isBlank() && dueAt != null && !dueAt.isBlank()) {
            return "RESOLVED";
        }
        if (relativeDate != null && !relativeDate.isBlank()) {
            return "UNRESOLVED";
        }
        if (dueDate != null && !dueDate.isBlank()) {
            return "RESOLVED";
        }
        return "NONE";
    }

    private static String deriveDateResolutionSource(String dueDate, String relativeDate, String dueAt) {
        if (relativeDate != null && !relativeDate.isBlank() && dueAt != null && !dueAt.isBlank()) {
            return "RELATIVE_DATE_EVIDENCE";
        }
        if (relativeDate != null && !relativeDate.isBlank()) {
            return "UNRESOLVED";
        }
        if (dueDate != null && !dueDate.isBlank()) {
            return "MODEL_PROVIDED_UNVERIFIED";
        }
        return "NONE";
    }

    public static UUID toSegmentUuid(String segmentId) {
        Objects.requireNonNull(segmentId, "segmentId");
        return UUID.nameUUIDFromBytes(("segment:" + segmentId).getBytes(StandardCharsets.UTF_8));
    }

    public static UUID toParticipantUuid(String displayName) {
        Objects.requireNonNull(displayName, "displayName");
        String identity = PersonIdentityNormalizer.identityKey(displayName);
        return UUID.nameUUIDFromBytes(("participant:" + identity)
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

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
