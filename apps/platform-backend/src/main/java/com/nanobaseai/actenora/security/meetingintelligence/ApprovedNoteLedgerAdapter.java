package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.meetingintelligence.api.ledger.ContinuityLedgerApi;
import com.nanobaseai.actenora.meetingintelligence.application.port.ActionItemRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedNoteLedgerPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.CommitmentRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.DecisionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.OpenQuestionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.RiskRepository;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;
import java.util.UUID;

/**
 * Appends approved note artifacts to the continuity ledger for prior-meeting carry-over.
 * Uses note aggregate ids as ledger ids for provenance + idempotency.
 */
public final class ApprovedNoteLedgerAdapter implements ApprovedNoteLedgerPort {

    private final DecisionRepository decisions;
    private final ActionItemRepository actionItems;
    private final CommitmentRepository commitments;
    private final RiskRepository risks;
    private final OpenQuestionRepository openQuestions;
    private final ContinuityLedgerApi ledgerApi;

    public ApprovedNoteLedgerAdapter(
            DecisionRepository decisions,
            ActionItemRepository actionItems,
            CommitmentRepository commitments,
            RiskRepository risks,
            OpenQuestionRepository openQuestions,
            ContinuityLedgerApi ledgerApi
    ) {
        this.decisions = Objects.requireNonNull(decisions, "decisions");
        this.actionItems = Objects.requireNonNull(actionItems, "actionItems");
        this.commitments = Objects.requireNonNull(commitments, "commitments");
        this.risks = Objects.requireNonNull(risks, "risks");
        this.openQuestions = Objects.requireNonNull(openQuestions, "openQuestions");
        this.ledgerApi = Objects.requireNonNull(ledgerApi, "ledgerApi");
    }

    @Override
    public void append(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID noteVersionId
    ) {
        decisions.findByNoteId(noteId, tenantId).stream()
                .filter(decision -> decision.noteVersionId().equals(noteVersionId))
                .forEach(decision -> ledgerApi.recordDecision(
                        tenantId,
                        meetingOccurrenceId,
                        noteId,
                        decision.id(),
                        decision.text()
                ));

        actionItems.findByNoteId(noteId, tenantId).stream()
                .filter(item -> item.noteVersionId().equals(noteVersionId))
                .forEach(item -> ledgerApi.recordActionItem(
                        tenantId,
                        meetingOccurrenceId,
                        noteId,
                        item.id(),
                        item.text()
                ));

        commitments.findByNoteId(noteId, tenantId).stream()
                .filter(commitment -> commitment.noteVersionId().equals(noteVersionId))
                .forEach(commitment -> ledgerApi.recordCommitment(
                        tenantId,
                        meetingOccurrenceId,
                        noteId,
                        commitment.id(),
                        commitment.text(),
                        commitment.owner(),
                        null
                ));

        risks.findByNoteId(noteId, tenantId).stream()
                .filter(risk -> risk.noteVersionId().equals(noteVersionId))
                .forEach(risk -> ledgerApi.recordRisk(
                        tenantId,
                        meetingOccurrenceId,
                        noteId,
                        risk.id(),
                        risk.text()
                ));

        openQuestions.findByNoteId(noteId, tenantId).stream()
                .filter(question -> question.noteVersionId().equals(noteVersionId))
                .forEach(question -> ledgerApi.recordOpenQuestion(
                        tenantId,
                        meetingOccurrenceId,
                        noteId,
                        question.id(),
                        question.text()
                ));
    }
}
