package com.nanobaseai.actenora.meetingintelligence.domain.ledger;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Previous-meeting brief assembled for the next occurrence (carry-overs + continuity).
 */
public record MeetingBrief(
        UUID briefId,
        TenantId tenantId,
        UUID targetOccurrenceId,
        Optional<UUID> previousOccurrenceId,
        Optional<UUID> meetingSeriesId,
        Optional<UUID> businessContextId,
        List<CarryOverItem> openTasks,
        List<CarryOverItem> openRisks,
        List<CarryOverItem> unresolvedQuestions,
        List<DecisionHistoryEntry> activeDecisions,
        List<CommitmentConfirmation> overdueCommitments,
        List<UUID> followUpChain,
        Instant generatedAt
) {

    public MeetingBrief {
        Objects.requireNonNull(briefId, "briefId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(targetOccurrenceId, "targetOccurrenceId");
        previousOccurrenceId = previousOccurrenceId == null ? Optional.empty() : previousOccurrenceId;
        meetingSeriesId = meetingSeriesId == null ? Optional.empty() : meetingSeriesId;
        businessContextId = businessContextId == null ? Optional.empty() : businessContextId;
        openTasks = List.copyOf(Objects.requireNonNull(openTasks, "openTasks"));
        openRisks = List.copyOf(Objects.requireNonNull(openRisks, "openRisks"));
        unresolvedQuestions = List.copyOf(Objects.requireNonNull(unresolvedQuestions, "unresolvedQuestions"));
        activeDecisions = List.copyOf(Objects.requireNonNull(activeDecisions, "activeDecisions"));
        overdueCommitments = List.copyOf(Objects.requireNonNull(overdueCommitments, "overdueCommitments"));
        followUpChain = List.copyOf(Objects.requireNonNull(followUpChain, "followUpChain"));
        Objects.requireNonNull(generatedAt, "generatedAt");
    }

    public record CarryOverItem(
            UUID itemId,
            String kind,
            String text,
            UUID sourceOccurrenceId
    ) {
        public CarryOverItem {
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(sourceOccurrenceId, "sourceOccurrenceId");
        }
    }
}
