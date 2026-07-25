package com.nanobaseai.actenora.meetingintelligence.domain.ledger.projection;

import com.nanobaseai.actenora.meetingintelligence.domain.ledger.CommitmentConfirmation;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuityProjection;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuityRelationSuggestion;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuitySuggestionStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContradictionCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContradictionStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.DecisionHistoryEntry;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.event.LedgerEvent;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItemStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.CommitmentConfirmationStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Idempotent event → projection applier. Rebuild replays all events through this class.
 */
public final class LedgerEventApplier {

    public void apply(LedgerProjectionState state, LedgerEvent event, LocalDate today) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(event, "event");
        if (!state.tenantId().equals(event.tenantId())) {
            throw new IllegalArgumentException("tenant mismatch on ledger event");
        }
        switch (event.type()) {
            case DECISION_RECORDED -> applyDecisionRecorded(state, event);
            case DECISION_SUPERSEDED -> applyDecisionSuperseded(state, event);
            case COMMITMENT_RECORDED -> applyCommitmentRecorded(state, event, today);
            case COMMITMENT_STATE_CHANGED -> applyCommitmentStateChanged(state, event, today);
            case ACTION_ITEM_RECORDED -> applyActionItemRecorded(state, event);
            case ACTION_ITEM_STATE_CHANGED -> applyActionItemStateChanged(state, event);
            case RISK_RECORDED -> applyRiskRecorded(state, event);
            case RISK_CLOSED -> applyRiskClosed(state, event);
            case OPEN_QUESTION_RECORDED -> applyOpenQuestionRecorded(state, event);
            case OPEN_QUESTION_RESOLVED -> applyOpenQuestionResolved(state, event);
            case OCCURRENCE_CONTINUITY_LINKED -> applyOccurrenceContinuityLinked(state, event);
            case FOLLOW_UP_LINKED -> applyFollowUpLinked(state, event);
            case RELATION_SUGGESTION_RECORDED -> applyRelationSuggestionRecorded(state, event);
            case RELATION_SUGGESTION_DECIDED -> applyRelationSuggestionDecided(state, event);
            case CONTRADICTION_PROPOSED -> applyContradictionProposed(state, event);
            case CONTRADICTION_DECIDED -> applyContradictionDecided(state, event);
        }
    }

    private void applyDecisionRecorded(LedgerProjectionState state, LedgerEvent event) {
        UUID noteId = UUID.fromString(event.require("noteId"));
        state.mapNote(noteId, event.meetingOccurrenceId());
        DecisionHistoryEntry entry = new DecisionHistoryEntry(
                event.aggregateId(),
                event.tenantId(),
                event.meetingOccurrenceId(),
                noteId,
                event.require("text"),
                Optional.empty(),
                Optional.empty(),
                true,
                event.occurredAt(),
                event.occurredAt()
        );
        state.putDecision(entry);
    }

    private void applyDecisionSuperseded(LedgerProjectionState state, LedgerEvent event) {
        UUID olderId = UUID.fromString(event.require("supersedesDecisionId"));
        DecisionHistoryEntry newer = state.decision(event.aggregateId())
                .orElseThrow(() -> new IllegalStateException("newer decision missing: " + event.aggregateId()));
        DecisionHistoryEntry older = state.decision(olderId)
                .orElseThrow(() -> new IllegalStateException("older decision missing: " + olderId));
        state.putDecision(older.markSupersededBy(newer.decisionId(), event.occurredAt()));
        state.putDecision(newer.withSupersedes(olderId, event.occurredAt()));
    }

    private void applyCommitmentRecorded(LedgerProjectionState state, LedgerEvent event, LocalDate today) {
        UUID noteId = UUID.fromString(event.require("noteId"));
        state.mapNote(noteId, event.meetingOccurrenceId());
        LocalDate dueDate = optionalDate(event.optional("dueDate"));
        CommitmentConfirmation confirmation = CommitmentConfirmation.create(
                event.aggregateId(),
                event.tenantId(),
                event.meetingOccurrenceId(),
                noteId,
                event.require("text"),
                event.optional("owner"),
                dueDate,
                event.occurredAt(),
                today
        );
        state.putCommitment(confirmation);
    }

    private void applyCommitmentStateChanged(LedgerProjectionState state, LedgerEvent event, LocalDate today) {
        CommitmentConfirmation existing = state.commitment(event.aggregateId())
                .orElseThrow(() -> new IllegalStateException("commitment missing: " + event.aggregateId()));
        CommitmentConfirmationStatus status = CommitmentConfirmationStatus.valueOf(event.require("status"));
        UUID actor = event.optional("decidedByUserId") == null
                ? null
                : UUID.fromString(event.require("decidedByUserId"));
        state.putCommitment(existing.withStatus(status, actor, event.occurredAt(), today));
    }

    private void applyActionItemRecorded(LedgerProjectionState state, LedgerEvent event) {
        UUID noteId = UUID.fromString(event.require("noteId"));
        state.mapNote(noteId, event.meetingOccurrenceId());
        state.putActionItem(new LedgerProjectionState.TrackedActionItem(
                event.aggregateId(),
                event.meetingOccurrenceId(),
                noteId,
                event.require("text"),
                ActionItemStatus.OPEN
        ));
    }

    private void applyActionItemStateChanged(LedgerProjectionState state, LedgerEvent event) {
        LedgerProjectionState.TrackedActionItem existing = state.actionItem(event.aggregateId())
                .orElseThrow(() -> new IllegalStateException("action item missing: " + event.aggregateId()));
        state.putActionItem(new LedgerProjectionState.TrackedActionItem(
                existing.id(),
                existing.meetingOccurrenceId(),
                existing.noteId(),
                existing.text(),
                ActionItemStatus.valueOf(event.require("status"))
        ));
    }

    private void applyRiskRecorded(LedgerProjectionState state, LedgerEvent event) {
        UUID noteId = UUID.fromString(event.require("noteId"));
        state.mapNote(noteId, event.meetingOccurrenceId());
        state.putRisk(new LedgerProjectionState.TrackedRisk(
                event.aggregateId(),
                event.meetingOccurrenceId(),
                noteId,
                event.require("text"),
                true
        ));
    }

    private void applyRiskClosed(LedgerProjectionState state, LedgerEvent event) {
        LedgerProjectionState.TrackedRisk existing = state.risk(event.aggregateId())
                .orElseThrow(() -> new IllegalStateException("risk missing: " + event.aggregateId()));
        state.putRisk(new LedgerProjectionState.TrackedRisk(
                existing.id(), existing.meetingOccurrenceId(), existing.noteId(), existing.text(), false
        ));
    }

    private void applyOpenQuestionRecorded(LedgerProjectionState state, LedgerEvent event) {
        UUID noteId = UUID.fromString(event.require("noteId"));
        state.mapNote(noteId, event.meetingOccurrenceId());
        state.putOpenQuestion(new LedgerProjectionState.TrackedOpenQuestion(
                event.aggregateId(),
                event.meetingOccurrenceId(),
                noteId,
                event.require("text"),
                true
        ));
    }

    private void applyOpenQuestionResolved(LedgerProjectionState state, LedgerEvent event) {
        LedgerProjectionState.TrackedOpenQuestion existing = state.openQuestion(event.aggregateId())
                .orElseThrow(() -> new IllegalStateException("open question missing: " + event.aggregateId()));
        state.putOpenQuestion(new LedgerProjectionState.TrackedOpenQuestion(
                existing.id(), existing.meetingOccurrenceId(), existing.noteId(), existing.text(), false
        ));
    }

    private void applyOccurrenceContinuityLinked(LedgerProjectionState state, LedgerEvent event) {
        UUID occurrenceId = event.meetingOccurrenceId();
        ContinuityProjection current = state.continuity(occurrenceId)
                .orElseGet(() -> ContinuityProjection.empty(event.tenantId(), occurrenceId, event.occurredAt()));

        UUID seriesId = optionalUuid(event.optional("meetingSeriesId"));
        UUID contextId = optionalUuid(event.optional("businessContextId"));
        UUID previousId = optionalUuid(event.optional("previousOccurrenceId"));

        ContinuityProjection updated = current;
        if (seriesId != null) {
            List<UUID> sameSeries = collectBySeries(state, event.tenantId(), seriesId, occurrenceId);
            updated = updated.withSeries(seriesId, sameSeries, event.occurredAt());
            refreshSeriesMembers(state, seriesId, sameSeries, event.occurredAt());
        }
        if (contextId != null) {
            List<UUID> sameContext = collectByContext(state, event.tenantId(), contextId, occurrenceId);
            updated = updated.withBusinessContext(contextId, sameContext, event.occurredAt());
            refreshContextMembers(state, contextId, sameContext, event.occurredAt());
        }
        if (previousId != null) {
            updated = updated.withNeighbors(previousId, updated.nextOccurrenceId().orElse(null), event.occurredAt());
            ContinuityProjection previous = state.continuity(previousId)
                    .orElseGet(() -> ContinuityProjection.empty(event.tenantId(), previousId, event.occurredAt()));
            state.putContinuity(previous.withNeighbors(
                    previous.previousOccurrenceId().orElse(null),
                    occurrenceId,
                    event.occurredAt()
            ));
        }
        state.putContinuity(updated);
    }

    private void applyFollowUpLinked(LedgerProjectionState state, LedgerEvent event) {
        UUID sourceId = UUID.fromString(event.require("sourceOccurrenceId"));
        UUID targetId = UUID.fromString(event.require("targetOccurrenceId"));
        ContinuityProjection source = state.continuity(sourceId)
                .orElseGet(() -> ContinuityProjection.empty(event.tenantId(), sourceId, event.occurredAt()));
        ContinuityProjection target = state.continuity(targetId)
                .orElseGet(() -> ContinuityProjection.empty(event.tenantId(), targetId, event.occurredAt()));

        List<UUID> chain = new ArrayList<>(source.followUpChain());
        if (chain.isEmpty()) {
            chain.add(sourceId);
        }
        if (!chain.contains(targetId)) {
            chain.add(targetId);
        }
        state.putContinuity(source.withFollowUpChain(chain, event.occurredAt())
                .withNeighbors(source.previousOccurrenceId().orElse(null), targetId, event.occurredAt()));
        state.putContinuity(target.withFollowUpChain(chain, event.occurredAt())
                .withNeighbors(sourceId, target.nextOccurrenceId().orElse(null), event.occurredAt()));
    }

    private void applyRelationSuggestionRecorded(LedgerProjectionState state, LedgerEvent event) {
        ContinuityRelationSuggestion suggestion = ContinuityRelationSuggestion.rehydrate(
                event.aggregateId(),
                event.tenantId(),
                UUID.fromString(event.require("sourceOccurrenceId")),
                UUID.fromString(event.require("targetOccurrenceId")),
                ContinuityRelationSuggestion.ProposedRelation.valueOf(event.require("proposedRelation")),
                new BigDecimal(event.require("confidence")),
                event.require("reason"),
                ContinuitySuggestionStatus.PENDING,
                event.occurredAt(),
                null,
                null
        );
        state.putSuggestion(suggestion);
    }

    private void applyRelationSuggestionDecided(LedgerProjectionState state, LedgerEvent event) {
        ContinuityRelationSuggestion existing = state.suggestion(event.aggregateId())
                .orElseThrow(() -> new IllegalStateException("suggestion missing: " + event.aggregateId()));
        boolean approve = Boolean.parseBoolean(event.require("approved"));
        String actor = event.require("actor");
        ContinuityRelationSuggestion decided = approve
                ? existing.approve(actor, event.occurredAt())
                : existing.reject(actor, event.occurredAt());
        state.putSuggestion(decided);
        if (approve) {
            materializeApprovedSuggestion(state, decided, event.occurredAt());
        }
    }

    private void materializeApprovedSuggestion(
            LedgerProjectionState state,
            ContinuityRelationSuggestion suggestion,
            Instant at
    ) {
        UUID sourceId = suggestion.sourceOccurrenceId();
        UUID targetId = suggestion.targetOccurrenceId();
        ContinuityProjection source = state.continuity(sourceId)
                .orElseGet(() -> ContinuityProjection.empty(suggestion.tenantId(), sourceId, at));
        ContinuityProjection target = state.continuity(targetId)
                .orElseGet(() -> ContinuityProjection.empty(suggestion.tenantId(), targetId, at));

        switch (suggestion.proposedRelation()) {
            case FOLLOW_UP -> {
                List<UUID> chain = new ArrayList<>(source.followUpChain());
                if (chain.isEmpty()) {
                    chain.add(sourceId);
                }
                if (!chain.contains(targetId)) {
                    chain.add(targetId);
                }
                state.putContinuity(source.withFollowUpChain(chain, at)
                        .withNeighbors(source.previousOccurrenceId().orElse(null), targetId, at));
                state.putContinuity(target.withFollowUpChain(chain, at)
                        .withNeighbors(sourceId, target.nextOccurrenceId().orElse(null), at));
            }
            case SAME_SERIES -> {
                UUID seriesId = source.meetingSeriesId().orElse(target.meetingSeriesId().orElse(UUID.randomUUID()));
                List<UUID> members = new ArrayList<>(Set.of(sourceId, targetId));
                source.sameSeriesOccurrenceIds().forEach(id -> {
                    if (!members.contains(id)) {
                        members.add(id);
                    }
                });
                target.sameSeriesOccurrenceIds().forEach(id -> {
                    if (!members.contains(id)) {
                        members.add(id);
                    }
                });
                state.putContinuity(source.withSeries(seriesId, members, at));
                state.putContinuity(target.withSeries(seriesId, members, at));
                refreshSeriesMembers(state, seriesId, members, at);
            }
            case SAME_BUSINESS_CONTEXT -> {
                UUID contextId = source.businessContextId()
                        .orElse(target.businessContextId().orElse(UUID.randomUUID()));
                List<UUID> members = new ArrayList<>(Set.of(sourceId, targetId));
                source.sameBusinessContextOccurrenceIds().forEach(id -> {
                    if (!members.contains(id)) {
                        members.add(id);
                    }
                });
                target.sameBusinessContextOccurrenceIds().forEach(id -> {
                    if (!members.contains(id)) {
                        members.add(id);
                    }
                });
                state.putContinuity(source.withBusinessContext(contextId, members, at));
                state.putContinuity(target.withBusinessContext(contextId, members, at));
                refreshContextMembers(state, contextId, members, at);
            }
        }
    }

    private void applyContradictionProposed(LedgerProjectionState state, LedgerEvent event) {
        ContradictionCandidate candidate = ContradictionCandidate.rehydrate(
                event.aggregateId(),
                event.tenantId(),
                event.meetingOccurrenceId(),
                UUID.fromString(event.require("leftDecisionId")),
                UUID.fromString(event.require("rightDecisionId")),
                event.require("reason"),
                new BigDecimal(event.require("confidence")),
                ContradictionStatus.PENDING,
                event.occurredAt(),
                null,
                null
        );
        state.putContradiction(candidate);
    }

    private void applyContradictionDecided(LedgerProjectionState state, LedgerEvent event) {
        ContradictionCandidate existing = state.contradiction(event.aggregateId())
                .orElseThrow(() -> new IllegalStateException("contradiction missing: " + event.aggregateId()));
        boolean confirm = Boolean.parseBoolean(event.require("confirmed"));
        String actor = event.require("actor");
        ContradictionCandidate decided = confirm
                ? existing.confirm(actor, event.occurredAt())
                : existing.reject(actor, event.occurredAt());
        state.putContradiction(decided);
    }

    private List<UUID> collectBySeries(
            LedgerProjectionState state,
            TenantId tenantId,
            UUID seriesId,
            UUID occurrenceId
    ) {
        Set<UUID> members = new LinkedHashSet<>();
        members.add(occurrenceId);
        state.continuities().stream()
                .filter(c -> c.tenantId().equals(tenantId))
                .filter(c -> c.meetingSeriesId().map(seriesId::equals).orElse(false))
                .map(ContinuityProjection::meetingOccurrenceId)
                .forEach(members::add);
        return new ArrayList<>(members);
    }

    private List<UUID> collectByContext(
            LedgerProjectionState state,
            TenantId tenantId,
            UUID contextId,
            UUID occurrenceId
    ) {
        Set<UUID> members = new LinkedHashSet<>();
        members.add(occurrenceId);
        state.continuities().stream()
                .filter(c -> c.tenantId().equals(tenantId))
                .filter(c -> c.businessContextId().map(contextId::equals).orElse(false))
                .map(ContinuityProjection::meetingOccurrenceId)
                .forEach(members::add);
        return new ArrayList<>(members);
    }

    private void refreshSeriesMembers(
            LedgerProjectionState state,
            UUID seriesId,
            List<UUID> members,
            Instant at
    ) {
        for (UUID memberId : members) {
            ContinuityProjection member = state.continuity(memberId)
                    .orElseGet(() -> ContinuityProjection.empty(
                            state.tenantId(), memberId, at));
            state.putContinuity(member.withSeries(seriesId, members, at));
        }
    }

    private void refreshContextMembers(
            LedgerProjectionState state,
            UUID contextId,
            List<UUID> members,
            Instant at
    ) {
        for (UUID memberId : members) {
            ContinuityProjection member = state.continuity(memberId)
                    .orElseGet(() -> ContinuityProjection.empty(
                            state.tenantId(), memberId, at));
            state.putContinuity(member.withBusinessContext(contextId, members, at));
        }
    }

    private static LocalDate optionalDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private static UUID optionalUuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    public static LocalDate todayUtc(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    public List<CommitmentConfirmation> overdueCommitments(LedgerProjectionState state) {
        return state.commitments().stream()
                .filter(CommitmentConfirmation::overdue)
                .sorted(Comparator.comparing(c -> c.dueDate().orElse(LocalDate.MAX)))
                .collect(Collectors.toList());
    }
}
