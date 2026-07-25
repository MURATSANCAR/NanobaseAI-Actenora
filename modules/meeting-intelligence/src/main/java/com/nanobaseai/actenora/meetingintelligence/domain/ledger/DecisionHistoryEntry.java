package com.nanobaseai.actenora.meetingintelligence.domain.ledger;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One decision row in the Decision Ledger, including supersede pointers.
 */
public record DecisionHistoryEntry(
        UUID decisionId,
        TenantId tenantId,
        UUID meetingOccurrenceId,
        UUID noteId,
        String text,
        Optional<UUID> supersedesDecisionId,
        Optional<UUID> supersededByDecisionId,
        boolean active,
        Instant recordedAt,
        Instant updatedAt
) {

    public DecisionHistoryEntry {
        Objects.requireNonNull(decisionId, "decisionId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        Objects.requireNonNull(noteId, "noteId");
        Objects.requireNonNull(text, "text");
        supersedesDecisionId = supersedesDecisionId == null ? Optional.empty() : supersedesDecisionId;
        supersededByDecisionId = supersededByDecisionId == null ? Optional.empty() : supersededByDecisionId;
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public DecisionHistoryEntry markSupersededBy(UUID newerDecisionId, Instant at) {
        return new DecisionHistoryEntry(
                decisionId,
                tenantId,
                meetingOccurrenceId,
                noteId,
                text,
                supersedesDecisionId,
                Optional.of(newerDecisionId),
                false,
                recordedAt,
                at
        );
    }

    public DecisionHistoryEntry withSupersedes(UUID olderDecisionId, Instant at) {
        return new DecisionHistoryEntry(
                decisionId,
                tenantId,
                meetingOccurrenceId,
                noteId,
                text,
                Optional.of(olderDecisionId),
                supersededByDecisionId,
                active,
                recordedAt,
                at
        );
    }
}
