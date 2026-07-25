package com.nanobaseai.actenora.meetingintelligence.domain.validation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable inputs for a single validation engine execution.
 */
public final class ValidationContext {

    private final UUID tenantId;
    private final UUID meetingOccurrenceId;
    private final UUID sourceExtractionId;
    private final List<ValidationCandidate> candidates;
    private final List<ValidationSegment> segments;
    private final List<ValidationParticipant> participants;
    private final QualityGateThreshold threshold;
    private final Map<UUID, ValidationSegment> segmentsById;

    public ValidationContext(
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID sourceExtractionId,
            List<ValidationCandidate> candidates,
            List<ValidationSegment> segments,
            List<ValidationParticipant> participants,
            QualityGateThreshold threshold
    ) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.meetingOccurrenceId = Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        this.sourceExtractionId = Objects.requireNonNull(sourceExtractionId, "sourceExtractionId");
        this.candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        this.segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        this.participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
        this.threshold = Objects.requireNonNull(threshold, "threshold");
        this.segmentsById = this.segments.stream()
                .collect(Collectors.toUnmodifiableMap(ValidationSegment::segmentId, Function.identity()));
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID meetingOccurrenceId() {
        return meetingOccurrenceId;
    }

    public UUID sourceExtractionId() {
        return sourceExtractionId;
    }

    public List<ValidationCandidate> candidates() {
        return candidates;
    }

    public List<ValidationSegment> segments() {
        return segments;
    }

    public List<ValidationParticipant> participants() {
        return participants;
    }

    public QualityGateThreshold threshold() {
        return threshold;
    }

    public Optional<ValidationSegment> segment(UUID segmentId) {
        return Optional.ofNullable(segmentsById.get(segmentId));
    }

    public String transcriptCorpus() {
        return segments.stream()
                .map(ValidationSegment::content)
                .collect(Collectors.joining("\n"));
    }

    public boolean isKnownParticipantId(String participantId) {
        if (participantId == null || participantId.isBlank()) {
            return false;
        }
        return participants.stream().anyMatch(p ->
                p.participantId().toString().equalsIgnoreCase(participantId)
                        || p.entraUserIdOptional().map(id -> id.equalsIgnoreCase(participantId)).orElse(false)
                        || p.emailOptional().map(email -> email.equalsIgnoreCase(participantId)).orElse(false)
        );
    }

    public boolean isKnownParticipantName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return false;
        }
        String normalized = normalize(displayName);
        return participants.stream()
                .anyMatch(p -> normalize(p.displayName()).equals(normalized));
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    public static boolean corpusContains(String corpus, String needle) {
        if (needle == null || needle.isBlank()) {
            return true;
        }
        return normalize(corpus).contains(normalize(needle));
    }
}
