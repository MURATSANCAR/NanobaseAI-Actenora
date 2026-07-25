package com.nanobaseai.actenora.meetingintelligence.api;

import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationParticipant;
import com.nanobaseai.actenora.meetingintelligence.domain.validation.ValidationSegment;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RunValidationCommand(
        UUID tenantId,
        UUID meetingOccurrenceId,
        UUID sourceExtractionId,
        List<ValidationCandidate> candidates,
        List<ValidationSegment> segments,
        List<ValidationParticipant> participants
) {
    public RunValidationCommand {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        Objects.requireNonNull(sourceExtractionId, "sourceExtractionId");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        participants = List.copyOf(Objects.requireNonNull(participants, "participants"));
    }
}
