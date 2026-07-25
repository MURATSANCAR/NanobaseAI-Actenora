package com.nanobaseai.actenora.meetingintelligence.domain.ledger.projection;

import com.nanobaseai.actenora.meetingintelligence.domain.ledger.CommitmentConfirmation;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuityProjection;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.DecisionHistoryEntry;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.MeetingBrief;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItemStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the next-meeting brief from continuity + open carry-over projections.
 */
public final class MeetingBriefGenerator {

    public MeetingBrief generate(
            LedgerProjectionState state,
            UUID targetOccurrenceId,
            Instant generatedAt
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(targetOccurrenceId, "targetOccurrenceId");
        Objects.requireNonNull(generatedAt, "generatedAt");

        ContinuityProjection continuity = state.continuity(targetOccurrenceId)
                .orElseGet(() -> ContinuityProjection.empty(state.tenantId(), targetOccurrenceId, generatedAt));

        Optional<UUID> previousId = continuity.previousOccurrenceId();
        List<UUID> sourceOccurrences = new ArrayList<>();
        previousId.ifPresent(sourceOccurrences::add);
        if (sourceOccurrences.isEmpty() && !continuity.followUpChain().isEmpty()) {
            List<UUID> chain = continuity.followUpChain();
            int idx = chain.indexOf(targetOccurrenceId);
            if (idx > 0) {
                sourceOccurrences.add(chain.get(idx - 1));
            }
        }

        List<MeetingBrief.CarryOverItem> openTasks = new ArrayList<>();
        List<MeetingBrief.CarryOverItem> openRisks = new ArrayList<>();
        List<MeetingBrief.CarryOverItem> unresolvedQuestions = new ArrayList<>();
        List<DecisionHistoryEntry> activeDecisions = new ArrayList<>();
        List<CommitmentConfirmation> overdueCommitments = new ArrayList<>();

        for (UUID sourceOccurrenceId : sourceOccurrences) {
            for (LedgerProjectionState.TrackedActionItem item : state.actionItems()) {
                if (item.meetingOccurrenceId().equals(sourceOccurrenceId)
                        && !item.status().isTerminal()
                        && item.status() != ActionItemStatus.CANCELLED) {
                    openTasks.add(new MeetingBrief.CarryOverItem(
                            item.id(), "ACTION_ITEM", item.text(), sourceOccurrenceId
                    ));
                }
            }
            for (LedgerProjectionState.TrackedRisk risk : state.risks()) {
                if (risk.meetingOccurrenceId().equals(sourceOccurrenceId) && risk.open()) {
                    openRisks.add(new MeetingBrief.CarryOverItem(
                            risk.id(), "RISK", risk.text(), sourceOccurrenceId
                    ));
                }
            }
            for (LedgerProjectionState.TrackedOpenQuestion question : state.openQuestions()) {
                if (question.meetingOccurrenceId().equals(sourceOccurrenceId) && question.unresolved()) {
                    unresolvedQuestions.add(new MeetingBrief.CarryOverItem(
                            question.id(), "OPEN_QUESTION", question.text(), sourceOccurrenceId
                    ));
                }
            }
            for (DecisionHistoryEntry decision : state.decisions()) {
                if (decision.meetingOccurrenceId().equals(sourceOccurrenceId) && decision.active()) {
                    activeDecisions.add(decision);
                }
            }
            for (CommitmentConfirmation commitment : state.commitments()) {
                if (commitment.meetingOccurrenceId().equals(sourceOccurrenceId) && commitment.overdue()) {
                    overdueCommitments.add(commitment);
                }
            }
        }

        TenantId tenantId = state.tenantId();
        return new MeetingBrief(
                UUID.randomUUID(),
                tenantId,
                targetOccurrenceId,
                previousId.isPresent() ? previousId : (sourceOccurrences.isEmpty()
                        ? Optional.empty()
                        : Optional.of(sourceOccurrences.getFirst())),
                continuity.meetingSeriesId(),
                continuity.businessContextId(),
                openTasks,
                openRisks,
                unresolvedQuestions,
                activeDecisions,
                overdueCommitments,
                continuity.followUpChain(),
                generatedAt
        );
    }
}
