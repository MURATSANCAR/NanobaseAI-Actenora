package com.nanobaseai.actenora.meetingintelligence.application.ledger;

import com.nanobaseai.actenora.meetingintelligence.application.ledger.port.LedgerEventStore;
import com.nanobaseai.actenora.meetingintelligence.application.ledger.port.LedgerProjectionRepository;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.CommitmentConfirmation;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuityProjection;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuityRelationSuggestion;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContradictionCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.DecisionHistory;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.DecisionHistoryEntry;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.MeetingBrief;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.event.LedgerEvent;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.event.LedgerEventType;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.projection.LedgerEventApplier;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.projection.LedgerProjectionState;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.projection.MeetingBriefGenerator;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.projection.ProjectionRebuilder;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItemStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.model.CommitmentConfirmationStatus;
import com.nanobaseai.actenora.meetingintelligence.domain.service.CommitmentConfirmationStateMachine;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Decision Ledger, Commitment Tracker, continuity projections, briefs, and contradiction flow.
 * All read models are event-sourced within the meetingintelligence schema.
 */
public final class ContinuityLedgerService {

    private final LedgerEventStore eventStore;
    private final LedgerProjectionRepository projectionRepository;
    private final LedgerEventApplier applier;
    private final ProjectionRebuilder rebuilder;
    private final MeetingBriefGenerator briefGenerator;
    private final Clock clock;

    public ContinuityLedgerService(
            LedgerEventStore eventStore,
            LedgerProjectionRepository projectionRepository,
            Clock clock
    ) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore");
        this.projectionRepository = Objects.requireNonNull(projectionRepository, "projectionRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.applier = new LedgerEventApplier();
        this.rebuilder = new ProjectionRebuilder(applier);
        this.briefGenerator = new MeetingBriefGenerator();
    }

    public DecisionHistoryEntry recordDecision(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID decisionId,
            String text
    ) {
        UUID id = decisionId == null ? UUID.randomUUID() : decisionId;
        Optional<DecisionHistoryEntry> existing = projectionRepository.getOrCreate(tenantId).decision(id);
        if (existing.isPresent()) {
            return existing.get();
        }
        Instant now = clock.instant();
        appendAndProject(LedgerEvent.create(
                tenantId,
                LedgerEventType.DECISION_RECORDED,
                "Decision",
                id,
                meetingOccurrenceId,
                now,
                eventStore.nextSequence(tenantId),
                mapOf("noteId", noteId.toString(), "text", text)
        ));
        return projectionRepository.getOrCreate(tenantId).decision(id).orElseThrow();
    }

    public DecisionHistory supersedeDecision(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID olderDecisionId,
            String newerText
    ) {
        DecisionHistoryEntry newer = recordDecision(
                tenantId, meetingOccurrenceId, noteId, null, newerText
        );
        Instant now = clock.instant();
        appendAndProject(LedgerEvent.create(
                tenantId,
                LedgerEventType.DECISION_SUPERSEDED,
                "Decision",
                newer.decisionId(),
                meetingOccurrenceId,
                now,
                eventStore.nextSequence(tenantId),
                mapOf("supersedesDecisionId", olderDecisionId.toString())
        ));
        LedgerProjectionState state = projectionRepository.getOrCreate(tenantId);
        DecisionHistoryEntry tip = state.decision(newer.decisionId()).orElseThrow();
        return DecisionHistory.fromChain(tip, List.copyOf(state.decisions()));
    }

    public CommitmentConfirmation recordCommitment(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            String text,
            String owner,
            LocalDate dueDate
    ) {
        return recordCommitment(tenantId, meetingOccurrenceId, noteId, null, text, owner, dueDate);
    }

    public CommitmentConfirmation recordCommitment(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID commitmentId,
            String text,
            String owner,
            LocalDate dueDate
    ) {
        UUID id = commitmentId == null ? UUID.randomUUID() : commitmentId;
        Optional<CommitmentConfirmation> existing = projectionRepository.getOrCreate(tenantId).commitment(id);
        if (existing.isPresent()) {
            return existing.get();
        }
        Instant now = clock.instant();
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("noteId", noteId.toString());
        payload.put("text", text);
        if (owner != null && !owner.isBlank()) {
            payload.put("owner", owner);
        }
        if (dueDate != null) {
            payload.put("dueDate", dueDate.toString());
        }
        appendAndProject(LedgerEvent.create(
                tenantId,
                LedgerEventType.COMMITMENT_RECORDED,
                "Commitment",
                id,
                meetingOccurrenceId,
                now,
                eventStore.nextSequence(tenantId),
                payload
        ));
        return projectionRepository.getOrCreate(tenantId).commitment(id).orElseThrow();
    }

    public CommitmentConfirmation confirmCommitment(TenantId tenantId, UUID commitmentId, UUID actorUserId) {
        return changeCommitmentState(tenantId, commitmentId, CommitmentConfirmationStatus.CONFIRMED, actorUserId);
    }

    public CommitmentConfirmation rejectCommitment(TenantId tenantId, UUID commitmentId, UUID actorUserId) {
        return changeCommitmentState(tenantId, commitmentId, CommitmentConfirmationStatus.REJECTED, actorUserId);
    }

    private CommitmentConfirmation changeCommitmentState(
            TenantId tenantId,
            UUID commitmentId,
            CommitmentConfirmationStatus target,
            UUID actorUserId
    ) {
        CommitmentConfirmation existing = projectionRepository.getOrCreate(tenantId)
                .commitment(commitmentId)
                .orElseThrow(() -> new IllegalArgumentException("commitment not found: " + commitmentId));
        CommitmentConfirmationStateMachine.assertTransition(existing.status(), target);
        Instant now = clock.instant();
        appendAndProject(LedgerEvent.create(
                tenantId,
                LedgerEventType.COMMITMENT_STATE_CHANGED,
                "Commitment",
                commitmentId,
                existing.meetingOccurrenceId(),
                now,
                eventStore.nextSequence(tenantId),
                mapOf(
                        "status", target.name(),
                        "decidedByUserId", actorUserId.toString()
                )
        ));
        return projectionRepository.getOrCreate(tenantId).commitment(commitmentId).orElseThrow();
    }

    public UUID recordActionItem(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            String text
    ) {
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        appendAndProject(LedgerEvent.create(
                tenantId,
                LedgerEventType.ACTION_ITEM_RECORDED,
                "ActionItem",
                id,
                meetingOccurrenceId,
                now,
                eventStore.nextSequence(tenantId),
                mapOf("noteId", noteId.toString(), "text", text)
        ));
        return id;
    }

    public void transitionActionItem(TenantId tenantId, UUID actionItemId, ActionItemStatus status) {
        LedgerProjectionState.TrackedActionItem existing = projectionRepository.getOrCreate(tenantId)
                .actionItem(actionItemId)
                .orElseThrow(() -> new IllegalArgumentException("action item not found: " + actionItemId));
        Instant now = clock.instant();
        appendAndProject(LedgerEvent.create(
                tenantId,
                LedgerEventType.ACTION_ITEM_STATE_CHANGED,
                "ActionItem",
                actionItemId,
                existing.meetingOccurrenceId(),
                now,
                eventStore.nextSequence(tenantId),
                mapOf("status", status.name())
        ));
    }

    public UUID recordRisk(TenantId tenantId, UUID meetingOccurrenceId, UUID noteId, String text) {
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        appendAndProject(LedgerEvent.create(
                tenantId,
                LedgerEventType.RISK_RECORDED,
                "Risk",
                id,
                meetingOccurrenceId,
                now,
                eventStore.nextSequence(tenantId),
                mapOf("noteId", noteId.toString(), "text", text)
        ));
        return id;
    }

    public void closeRisk(TenantId tenantId, UUID riskId) {
        LedgerProjectionState.TrackedRisk existing = projectionRepository.getOrCreate(tenantId)
                .risk(riskId)
                .orElseThrow(() -> new IllegalArgumentException("risk not found: " + riskId));
        Instant now = clock.instant();
        appendAndProject(LedgerEvent.create(
                tenantId,
                LedgerEventType.RISK_CLOSED,
                "Risk",
                riskId,
                existing.meetingOccurrenceId(),
                now,
                eventStore.nextSequence(tenantId),
                Map.of()
        ));
    }

    public UUID recordOpenQuestion(TenantId tenantId, UUID meetingOccurrenceId, UUID noteId, String text) {
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        appendAndProject(LedgerEvent.create(
                tenantId,
                LedgerEventType.OPEN_QUESTION_RECORDED,
                "OpenQuestion",
                id,
                meetingOccurrenceId,
                now,
                eventStore.nextSequence(tenantId),
                mapOf("noteId", noteId.toString(), "text", text)
        ));
        return id;
    }

    public void resolveOpenQuestion(TenantId tenantId, UUID questionId) {
        LedgerProjectionState.TrackedOpenQuestion existing = projectionRepository.getOrCreate(tenantId)
                .openQuestion(questionId)
                .orElseThrow(() -> new IllegalArgumentException("open question not found: " + questionId));
        Instant now = clock.instant();
        appendAndProject(LedgerEvent.create(
                tenantId,
                LedgerEventType.OPEN_QUESTION_RESOLVED,
                "OpenQuestion",
                questionId,
                existing.meetingOccurrenceId(),
                now,
                eventStore.nextSequence(tenantId),
                Map.of()
        ));
    }

    public ContinuityProjection linkOccurrenceContinuity(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID meetingSeriesId,
            UUID businessContextId,
            UUID previousOccurrenceId
    ) {
        Instant now = clock.instant();
        Map<String, String> payload = new LinkedHashMap<>();
        if (meetingSeriesId != null) {
            payload.put("meetingSeriesId", meetingSeriesId.toString());
        }
        if (businessContextId != null) {
            payload.put("businessContextId", businessContextId.toString());
        }
        if (previousOccurrenceId != null) {
            payload.put("previousOccurrenceId", previousOccurrenceId.toString());
        }
        appendAndProject(LedgerEvent.create(
                tenantId,
                LedgerEventType.OCCURRENCE_CONTINUITY_LINKED,
                "Continuity",
                meetingOccurrenceId,
                meetingOccurrenceId,
                now,
                eventStore.nextSequence(tenantId),
                payload
        ));
        return projectionRepository.getOrCreate(tenantId).continuity(meetingOccurrenceId).orElseThrow();
    }

    public void linkFollowUp(TenantId tenantId, UUID sourceOccurrenceId, UUID targetOccurrenceId) {
        Instant now = clock.instant();
        appendAndProject(LedgerEvent.create(
                tenantId,
                LedgerEventType.FOLLOW_UP_LINKED,
                "FollowUp",
                UUID.randomUUID(),
                sourceOccurrenceId,
                now,
                eventStore.nextSequence(tenantId),
                mapOf(
                        "sourceOccurrenceId", sourceOccurrenceId.toString(),
                        "targetOccurrenceId", targetOccurrenceId.toString()
                )
        ));
    }

    public ContinuityRelationSuggestion recordRelationSuggestion(
            TenantId tenantId,
            UUID sourceOccurrenceId,
            UUID targetOccurrenceId,
            ContinuityRelationSuggestion.ProposedRelation proposedRelation,
            BigDecimal confidence,
            String reason
    ) {
        ContinuityRelationSuggestion suggestion = ContinuityRelationSuggestion.propose(
                tenantId, sourceOccurrenceId, targetOccurrenceId, proposedRelation, confidence, reason, clock.instant()
        );
        Instant now = clock.instant();
        appendAndProject(LedgerEvent.create(
                tenantId,
                LedgerEventType.RELATION_SUGGESTION_RECORDED,
                "ContinuityRelationSuggestion",
                suggestion.id(),
                sourceOccurrenceId,
                now,
                eventStore.nextSequence(tenantId),
                mapOf(
                        "sourceOccurrenceId", sourceOccurrenceId.toString(),
                        "targetOccurrenceId", targetOccurrenceId.toString(),
                        "proposedRelation", proposedRelation.name(),
                        "confidence", confidence.toPlainString(),
                        "reason", reason
                )
        ));
        return projectionRepository.getOrCreate(tenantId).suggestion(suggestion.id()).orElseThrow();
    }

    public Optional<ContinuityRelationSuggestion> decideRelationSuggestion(
            TenantId tenantId,
            UUID suggestionId,
            boolean approve,
            String actor
    ) {
        ContinuityRelationSuggestion existing = projectionRepository.getOrCreate(tenantId)
                .suggestion(suggestionId)
                .orElseThrow(() -> new IllegalArgumentException("suggestion not found: " + suggestionId));
        Instant now = clock.instant();
        appendAndProject(LedgerEvent.create(
                tenantId,
                LedgerEventType.RELATION_SUGGESTION_DECIDED,
                "ContinuityRelationSuggestion",
                suggestionId,
                existing.sourceOccurrenceId(),
                now,
                eventStore.nextSequence(tenantId),
                mapOf("approved", Boolean.toString(approve), "actor", actor)
        ));
        ContinuityRelationSuggestion decided = projectionRepository.getOrCreate(tenantId)
                .suggestion(suggestionId)
                .orElseThrow();
        return Optional.of(decided);
    }

    public ContradictionCandidate proposeContradiction(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID leftDecisionId,
            UUID rightDecisionId,
            String reason,
            BigDecimal confidence
    ) {
        ContradictionCandidate candidate = ContradictionCandidate.propose(
                tenantId, meetingOccurrenceId, leftDecisionId, rightDecisionId, reason, confidence, clock.instant()
        );
        Instant now = clock.instant();
        appendAndProject(LedgerEvent.create(
                tenantId,
                LedgerEventType.CONTRADICTION_PROPOSED,
                "ContradictionCandidate",
                candidate.id(),
                meetingOccurrenceId,
                now,
                eventStore.nextSequence(tenantId),
                mapOf(
                        "leftDecisionId", leftDecisionId.toString(),
                        "rightDecisionId", rightDecisionId.toString(),
                        "reason", reason,
                        "confidence", confidence.toPlainString()
                )
        ));
        return projectionRepository.getOrCreate(tenantId).contradiction(candidate.id()).orElseThrow();
    }

    public ContradictionCandidate decideContradiction(
            TenantId tenantId,
            UUID contradictionId,
            boolean confirm,
            String actor
    ) {
        ContradictionCandidate existing = projectionRepository.getOrCreate(tenantId)
                .contradiction(contradictionId)
                .orElseThrow(() -> new IllegalArgumentException("contradiction not found: " + contradictionId));
        Instant now = clock.instant();
        appendAndProject(LedgerEvent.create(
                tenantId,
                LedgerEventType.CONTRADICTION_DECIDED,
                "ContradictionCandidate",
                contradictionId,
                existing.meetingOccurrenceId(),
                now,
                eventStore.nextSequence(tenantId),
                mapOf("confirmed", Boolean.toString(confirm), "actor", actor)
        ));
        return projectionRepository.getOrCreate(tenantId).contradiction(contradictionId).orElseThrow();
    }

    public MeetingBrief generateBrief(TenantId tenantId, UUID targetOccurrenceId) {
        LedgerProjectionState state = projectionRepository.getOrCreate(tenantId);
        return briefGenerator.generate(state, targetOccurrenceId, clock.instant());
    }

    public ContinuityProjection continuity(TenantId tenantId, UUID occurrenceId) {
        return projectionRepository.getOrCreate(tenantId)
                .continuity(occurrenceId)
                .orElseGet(() -> ContinuityProjection.empty(tenantId, occurrenceId, clock.instant()));
    }

    public List<DecisionHistoryEntry> listDecisions(TenantId tenantId) {
        return List.copyOf(projectionRepository.getOrCreate(tenantId).decisions());
    }

    public Optional<DecisionHistory> decisionHistory(TenantId tenantId, UUID decisionId) {
        LedgerProjectionState state = projectionRepository.getOrCreate(tenantId);
        return state.decision(decisionId)
                .map(tip -> DecisionHistory.fromChain(tip, List.copyOf(state.decisions())));
    }

    public List<CommitmentConfirmation> listCommitments(TenantId tenantId) {
        return List.copyOf(projectionRepository.getOrCreate(tenantId).commitments());
    }

    public List<CommitmentConfirmation> listOverdueCommitments(TenantId tenantId) {
        return applier.overdueCommitments(projectionRepository.getOrCreate(tenantId));
    }

    public List<LedgerProjectionState.TrackedActionItem> listOpenTasks(TenantId tenantId, UUID occurrenceId) {
        return projectionRepository.getOrCreate(tenantId).actionItems().stream()
                .filter(item -> item.meetingOccurrenceId().equals(occurrenceId))
                .filter(item -> !item.status().isTerminal())
                .toList();
    }

    public List<LedgerProjectionState.TrackedActionItem> listOpenActionItems(TenantId tenantId) {
        return projectionRepository.getOrCreate(tenantId).actionItems().stream()
                .filter(item -> !item.status().isTerminal())
                .toList();
    }

    public Optional<LedgerProjectionState.TrackedActionItem> findActionItem(TenantId tenantId, UUID actionItemId) {
        return projectionRepository.getOrCreate(tenantId).actionItem(actionItemId);
    }

    public List<ContradictionCandidate> listContradictions(TenantId tenantId) {
        return List.copyOf(projectionRepository.getOrCreate(tenantId).contradictions());
    }

    public List<ContinuityRelationSuggestion> listSuggestions(TenantId tenantId) {
        return List.copyOf(projectionRepository.getOrCreate(tenantId).suggestions());
    }

    public List<LedgerEvent> listEvents(TenantId tenantId) {
        return eventStore.findAllByTenant(tenantId);
    }

    /**
     * Rebuild all read models for a tenant from the event stream (no cross-schema joins).
     */
    public LedgerProjectionState rebuildProjections(TenantId tenantId) {
        LocalDate today = LedgerEventApplier.todayUtc(clock.instant());
        LedgerProjectionState rebuilt = rebuilder.rebuild(
                tenantId,
                eventStore.findAllByTenant(tenantId),
                today
        );
        projectionRepository.replace(tenantId, rebuilt);
        return rebuilt;
    }

    private void appendAndProject(LedgerEvent event) {
        eventStore.append(event);
        LedgerProjectionState state = projectionRepository.getOrCreate(event.tenantId());
        applier.apply(state, event, LedgerEventApplier.todayUtc(clock.instant()));
        projectionRepository.save(state);
    }

    private static Map<String, String> mapOf(String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must be even");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
