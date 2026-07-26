package com.nanobaseai.actenora.security.meetingintelligence;

import com.nanobaseai.actenora.meetingintelligence.api.ledger.ContinuityLedgerApi;
import com.nanobaseai.actenora.meetingintelligence.application.port.ApprovedNoteLedgerPort;
import com.nanobaseai.actenora.meetingintelligence.application.port.CommitmentRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.DecisionRepository;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;
import java.util.UUID;

/**
 * Appends artifacts belonging to the approved note version to the continuity ledger.
 * Uses note Decision/Commitment ids as ledger aggregate ids for provenance + idempotency (FAZ 28).
 */
public final class ApprovedNoteLedgerAdapter implements ApprovedNoteLedgerPort {

    private final DecisionRepository decisions;
    private final CommitmentRepository commitments;
    private final ContinuityLedgerApi ledgerApi;

    public ApprovedNoteLedgerAdapter(
            DecisionRepository decisions,
            CommitmentRepository commitments,
            ContinuityLedgerApi ledgerApi
    ) {
        this.decisions = Objects.requireNonNull(decisions, "decisions");
        this.commitments = Objects.requireNonNull(commitments, "commitments");
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
    }
}
