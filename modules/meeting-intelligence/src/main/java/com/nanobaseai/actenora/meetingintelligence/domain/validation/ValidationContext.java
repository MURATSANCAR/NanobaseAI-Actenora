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

    public String evidenceCorpus(ValidationCandidate candidate) {
        if (candidate == null || candidate.evidenceSegmentIds().isEmpty()) {
            return "";
        }
        return candidate.evidenceSegmentIds().stream()
                .map(segmentsById::get)
                .filter(Objects::nonNull)
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
        if (normalized.isBlank()) {
            return false;
        }
        String firstToken = firstToken(normalized);
        int tokenCount = tokenCount(normalized);

        return participants.stream().anyMatch(p -> {
            String participantNorm = normalize(p.displayName());
            if (participantNorm.equals(normalized)) {
                return true;
            }
            String participantFirst = firstToken(participantNorm);
            int participantTokenCount = tokenCount(participantNorm);

            // Allow short first-name owners to match full multi-token speaker names.
            if (tokenCount == 1 && participantFirst.equals(firstToken)) {
                return true;
            }
            // Vice-versa: allow full owner names to match single-token participant names.
            if (participantTokenCount == 1 && participantFirst.equals(firstToken)) {
                return true;
            }
            return false;
        });
    }

    public boolean ownerMatchesEvidenceSpeaker(ValidationCandidate candidate) {
        if (candidate == null || candidate.ownerDisplayName().isEmpty()) {
            return false;
        }
        String owner = normalize(candidate.ownerDisplayName().orElseThrow());
        if (owner.isBlank()) {
            return false;
        }
        for (UUID segmentId : candidate.evidenceSegmentIds()) {
            ValidationSegment segment = segmentsById.get(segmentId);
            if (segment == null) {
                continue;
            }
            String speaker = normalize(segment.speakerDisplayName());
            if (!speaker.isBlank() && namesEquivalent(owner, speaker)) {
                return true;
            }
        }
        return false;
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase()
                .replace('ı', 'i')
                .replace('İ', 'i')
                .replace('ş', 's')
                .replace('Ş', 's')
                .replace('ğ', 'g')
                .replace('Ğ', 'g')
                .replace('ç', 'c')
                .replace('Ç', 'c')
                .replace('ö', 'o')
                .replace('Ö', 'o')
                .replace('ü', 'u')
                .replace('Ü', 'u')
                .replaceAll("[^\\p{Alnum}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean namesEquivalent(String left, String right) {
        if (left.equals(right)) {
            return true;
        }
        String leftFirst = firstToken(left);
        String rightFirst = firstToken(right);
        if (leftFirst.isBlank() || rightFirst.isBlank()) {
            return false;
        }
        return leftFirst.equals(rightFirst);
    }

    private static String firstToken(String normalizedValue) {
        if (normalizedValue == null || normalizedValue.isBlank()) {
            return "";
        }
        int idx = normalizedValue.indexOf(' ');
        return idx < 0 ? normalizedValue : normalizedValue.substring(0, idx);
    }

    private static int tokenCount(String normalizedValue) {
        if (normalizedValue == null || normalizedValue.isBlank()) {
            return 0;
        }
        int count = 1;
        for (int i = 0; i < normalizedValue.length(); i++) {
            if (normalizedValue.charAt(i) == ' ') {
                count++;
            }
        }
        return count;
    }

    public static boolean corpusContains(String corpus, String needle) {
        if (needle == null || needle.isBlank()) {
            return true;
        }
        return normalize(corpus).contains(normalize(needle));
    }
}
