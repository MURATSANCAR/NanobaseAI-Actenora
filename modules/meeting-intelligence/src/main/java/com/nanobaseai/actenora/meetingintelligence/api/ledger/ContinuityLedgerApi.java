package com.nanobaseai.actenora.meetingintelligence.api.ledger;

import com.nanobaseai.actenora.meetingintelligence.application.ledger.ContinuityLedgerService;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.CommitmentConfirmation;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuityProjection;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuityRelationSuggestion;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContradictionCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.DecisionHistory;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.DecisionHistoryEntry;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.MeetingBrief;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.projection.LedgerProjectionState;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Public façade for Decision Ledger, Commitment Tracker, and continuity briefs.
 */
public final class ContinuityLedgerApi {

    private final ContinuityLedgerService service;

    public ContinuityLedgerApi(ContinuityLedgerService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public DecisionHistoryEntry recordDecision(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            String text
    ) {
        return service.recordDecision(tenantId, meetingOccurrenceId, noteId, null, text);
    }

    /**
     * Records a decision using a stable source id (e.g. note Decision aggregate id). Idempotent.
     */
    public DecisionHistoryEntry recordDecision(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID decisionId,
            String text
    ) {
        return service.recordDecision(tenantId, meetingOccurrenceId, noteId, decisionId, text);
    }

    public DecisionHistory supersedeDecision(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID olderDecisionId,
            String newerText
    ) {
        return service.supersedeDecision(tenantId, meetingOccurrenceId, noteId, olderDecisionId, newerText);
    }

    public Optional<DecisionHistory> decisionHistory(TenantId tenantId, UUID decisionId) {
        return service.decisionHistory(tenantId, decisionId);
    }

    public CommitmentConfirmation recordCommitment(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            String text,
            String owner,
            LocalDate dueDate
    ) {
        return service.recordCommitment(tenantId, meetingOccurrenceId, noteId, null, text, owner, dueDate);
    }

    /**
     * Records a commitment using a stable source id (e.g. note Commitment aggregate id). Idempotent.
     */
    public CommitmentConfirmation recordCommitment(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID commitmentId,
            String text,
            String owner,
            LocalDate dueDate
    ) {
        return service.recordCommitment(
                tenantId, meetingOccurrenceId, noteId, commitmentId, text, owner, dueDate
        );
    }

    public CommitmentConfirmation confirmCommitment(TenantId tenantId, UUID commitmentId, UUID actorUserId) {
        return service.confirmCommitment(tenantId, commitmentId, actorUserId);
    }

    public CommitmentConfirmation rejectCommitment(TenantId tenantId, UUID commitmentId, UUID actorUserId) {
        return service.rejectCommitment(tenantId, commitmentId, actorUserId);
    }

    public List<CommitmentConfirmation> overdueCommitments(TenantId tenantId) {
        return service.listOverdueCommitments(tenantId);
    }

    public MeetingBrief generateBrief(TenantId tenantId, UUID targetOccurrenceId) {
        return service.generateBrief(tenantId, targetOccurrenceId);
    }

    public ContinuityProjection continuity(TenantId tenantId, UUID occurrenceId) {
        return service.continuity(tenantId, occurrenceId);
    }

    public ContinuityProjection linkContinuity(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID meetingSeriesId,
            UUID businessContextId,
            UUID previousOccurrenceId
    ) {
        return service.linkOccurrenceContinuity(
                tenantId, meetingOccurrenceId, meetingSeriesId, businessContextId, previousOccurrenceId
        );
    }

    public ContinuityRelationSuggestion recordRelationSuggestion(
            TenantId tenantId,
            UUID sourceOccurrenceId,
            UUID targetOccurrenceId,
            ContinuityRelationSuggestion.ProposedRelation proposedRelation,
            BigDecimal confidence,
            String reason
    ) {
        return service.recordRelationSuggestion(
                tenantId, sourceOccurrenceId, targetOccurrenceId, proposedRelation, confidence, reason
        );
    }

    public ContinuityRelationSuggestion approveRelationSuggestion(
            TenantId tenantId,
            UUID suggestionId,
            String actor
    ) {
        return service.decideRelationSuggestion(tenantId, suggestionId, true, actor)
                .orElseThrow(() -> new IllegalArgumentException("suggestion not found: " + suggestionId));
    }

    public ContinuityRelationSuggestion rejectRelationSuggestion(
            TenantId tenantId,
            UUID suggestionId,
            String actor
    ) {
        return service.decideRelationSuggestion(tenantId, suggestionId, false, actor)
                .orElseThrow(() -> new IllegalArgumentException("suggestion not found: " + suggestionId));
    }

    public List<ContinuityRelationSuggestion> listSuggestions(TenantId tenantId) {
        return service.listSuggestions(tenantId);
    }

    public List<ContradictionCandidate> listContradictions(TenantId tenantId) {
        return service.listContradictions(tenantId);
    }

    public List<com.nanobaseai.actenora.meetingintelligence.domain.ledger.event.LedgerEvent> listEvents(
            TenantId tenantId
    ) {
        return service.listEvents(tenantId);
    }

    public ContradictionCandidate proposeContradiction(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID leftDecisionId,
            UUID rightDecisionId,
            String reason,
            BigDecimal confidence
    ) {
        return service.proposeContradiction(
                tenantId, meetingOccurrenceId, leftDecisionId, rightDecisionId, reason, confidence
        );
    }

    public ContradictionCandidate confirmContradiction(TenantId tenantId, UUID contradictionId, String actor) {
        return service.decideContradiction(tenantId, contradictionId, true, actor);
    }

    public ContradictionCandidate rejectContradiction(TenantId tenantId, UUID contradictionId, String actor) {
        return service.decideContradiction(tenantId, contradictionId, false, actor);
    }

    public LedgerProjectionState rebuildProjections(TenantId tenantId) {
        return service.rebuildProjections(tenantId);
    }
}
