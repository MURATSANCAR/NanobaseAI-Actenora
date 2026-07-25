package com.nanobaseai.actenora.meetingintelligence.domain.ledger.projection;

import com.nanobaseai.actenora.meetingintelligence.domain.ledger.CommitmentConfirmation;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuityProjection;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContinuityRelationSuggestion;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.ContradictionCandidate;
import com.nanobaseai.actenora.meetingintelligence.domain.ledger.DecisionHistoryEntry;
import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItemStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory mutable projection workspace used by the event applier and rebuild.
 */
public final class LedgerProjectionState {

    private final TenantId tenantId;
    private final Map<UUID, DecisionHistoryEntry> decisions = new LinkedHashMap<>();
    private final Map<UUID, CommitmentConfirmation> commitments = new LinkedHashMap<>();
    private final Map<UUID, TrackedActionItem> actionItems = new LinkedHashMap<>();
    private final Map<UUID, TrackedRisk> risks = new LinkedHashMap<>();
    private final Map<UUID, TrackedOpenQuestion> openQuestions = new LinkedHashMap<>();
    private final Map<UUID, ContinuityProjection> continuityByOccurrence = new LinkedHashMap<>();
    private final Map<UUID, ContradictionCandidate> contradictions = new LinkedHashMap<>();
    private final Map<UUID, ContinuityRelationSuggestion> suggestions = new LinkedHashMap<>();
    private final Map<UUID, UUID> noteToOccurrence = new LinkedHashMap<>();

    public LedgerProjectionState(TenantId tenantId) {
        this.tenantId = tenantId;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public void putDecision(DecisionHistoryEntry entry) {
        decisions.put(entry.decisionId(), entry);
    }

    public Optional<DecisionHistoryEntry> decision(UUID id) {
        return Optional.ofNullable(decisions.get(id));
    }

    public Collection<DecisionHistoryEntry> decisions() {
        return decisions.values();
    }

    public void putCommitment(CommitmentConfirmation confirmation) {
        commitments.put(confirmation.commitmentId(), confirmation);
    }

    public Optional<CommitmentConfirmation> commitment(UUID id) {
        return Optional.ofNullable(commitments.get(id));
    }

    public Collection<CommitmentConfirmation> commitments() {
        return commitments.values();
    }

    public void putActionItem(TrackedActionItem item) {
        actionItems.put(item.id(), item);
    }

    public Optional<TrackedActionItem> actionItem(UUID id) {
        return Optional.ofNullable(actionItems.get(id));
    }

    public Collection<TrackedActionItem> actionItems() {
        return actionItems.values();
    }

    public void putRisk(TrackedRisk risk) {
        risks.put(risk.id(), risk);
    }

    public Optional<TrackedRisk> risk(UUID id) {
        return Optional.ofNullable(risks.get(id));
    }

    public Collection<TrackedRisk> risks() {
        return risks.values();
    }

    public void putOpenQuestion(TrackedOpenQuestion question) {
        openQuestions.put(question.id(), question);
    }

    public Optional<TrackedOpenQuestion> openQuestion(UUID id) {
        return Optional.ofNullable(openQuestions.get(id));
    }

    public Collection<TrackedOpenQuestion> openQuestions() {
        return openQuestions.values();
    }

    public void putContinuity(ContinuityProjection projection) {
        continuityByOccurrence.put(projection.meetingOccurrenceId(), projection);
    }

    public Optional<ContinuityProjection> continuity(UUID occurrenceId) {
        return Optional.ofNullable(continuityByOccurrence.get(occurrenceId));
    }

    public Collection<ContinuityProjection> continuities() {
        return continuityByOccurrence.values();
    }

    public void putContradiction(ContradictionCandidate candidate) {
        contradictions.put(candidate.id(), candidate);
    }

    public Optional<ContradictionCandidate> contradiction(UUID id) {
        return Optional.ofNullable(contradictions.get(id));
    }

    public Collection<ContradictionCandidate> contradictions() {
        return contradictions.values();
    }

    public void putSuggestion(ContinuityRelationSuggestion suggestion) {
        suggestions.put(suggestion.id(), suggestion);
    }

    public Optional<ContinuityRelationSuggestion> suggestion(UUID id) {
        return Optional.ofNullable(suggestions.get(id));
    }

    public Collection<ContinuityRelationSuggestion> suggestions() {
        return suggestions.values();
    }

    public void mapNote(UUID noteId, UUID occurrenceId) {
        noteToOccurrence.put(noteId, occurrenceId);
    }

    public Optional<UUID> occurrenceForNote(UUID noteId) {
        return Optional.ofNullable(noteToOccurrence.get(noteId));
    }

    public void clear() {
        decisions.clear();
        commitments.clear();
        actionItems.clear();
        risks.clear();
        openQuestions.clear();
        continuityByOccurrence.clear();
        contradictions.clear();
        suggestions.clear();
        noteToOccurrence.clear();
    }

    public record TrackedActionItem(
            UUID id,
            UUID meetingOccurrenceId,
            UUID noteId,
            String text,
            ActionItemStatus status
    ) {
    }

    public record TrackedRisk(
            UUID id,
            UUID meetingOccurrenceId,
            UUID noteId,
            String text,
            boolean open
    ) {
    }

    public record TrackedOpenQuestion(
            UUID id,
            UUID meetingOccurrenceId,
            UUID noteId,
            String text,
            boolean unresolved
    ) {
    }
}
