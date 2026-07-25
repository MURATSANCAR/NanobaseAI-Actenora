package com.nanobaseai.actenora.meetingintelligence.domain.ledger;

import com.nanobaseai.actenora.meetingintelligence.domain.model.CommitmentConfirmationStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Commitment Tracker read model — confirmation state plus overdue projection.
 */
public final class CommitmentConfirmation {

    private final UUID commitmentId;
    private final TenantId tenantId;
    private final UUID meetingOccurrenceId;
    private final UUID noteId;
    private final String text;
    private final String owner;
    private final CommitmentConfirmationStatus status;
    private final LocalDate dueDate;
    private final boolean overdue;
    private final Instant recordedAt;
    private final Instant updatedAt;
    private final Instant decidedAt;
    private final UUID decidedByUserId;

    private CommitmentConfirmation(
            UUID commitmentId,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            String text,
            String owner,
            CommitmentConfirmationStatus status,
            LocalDate dueDate,
            boolean overdue,
            Instant recordedAt,
            Instant updatedAt,
            Instant decidedAt,
            UUID decidedByUserId
    ) {
        this.commitmentId = Objects.requireNonNull(commitmentId, "commitmentId");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.meetingOccurrenceId = Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        this.noteId = Objects.requireNonNull(noteId, "noteId");
        this.text = Objects.requireNonNull(text, "text");
        this.owner = owner;
        this.status = Objects.requireNonNull(status, "status");
        this.dueDate = dueDate;
        this.overdue = overdue;
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.decidedAt = decidedAt;
        this.decidedByUserId = decidedByUserId;
    }

    public static CommitmentConfirmation create(
            UUID commitmentId,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            String text,
            String owner,
            LocalDate dueDate,
            Instant recordedAt,
            LocalDate today
    ) {
        CommitmentConfirmationStatus status = CommitmentConfirmationStatus.PENDING_CONFIRMATION;
        return new CommitmentConfirmation(
                commitmentId,
                tenantId,
                meetingOccurrenceId,
                noteId,
                text,
                owner,
                status,
                dueDate,
                OverdueCalculator.isOverdue(dueDate, status, today),
                recordedAt,
                recordedAt,
                null,
                null
        );
    }

    public CommitmentConfirmation withStatus(
            CommitmentConfirmationStatus status,
            UUID decidedByUserId,
            Instant at,
            LocalDate today
    ) {
        return new CommitmentConfirmation(
                commitmentId,
                tenantId,
                meetingOccurrenceId,
                noteId,
                text,
                owner,
                status,
                dueDate,
                OverdueCalculator.isOverdue(dueDate, status, today),
                recordedAt,
                at,
                at,
                decidedByUserId
        );
    }

    public CommitmentConfirmation recalculateOverdue(LocalDate today) {
        return new CommitmentConfirmation(
                commitmentId,
                tenantId,
                meetingOccurrenceId,
                noteId,
                text,
                owner,
                status,
                dueDate,
                OverdueCalculator.isOverdue(dueDate, status, today),
                recordedAt,
                updatedAt,
                decidedAt,
                decidedByUserId
        );
    }

    public UUID commitmentId() { return commitmentId; }
    public TenantId tenantId() { return tenantId; }
    public UUID meetingOccurrenceId() { return meetingOccurrenceId; }
    public UUID noteId() { return noteId; }
    public String text() { return text; }
    public Optional<String> owner() { return Optional.ofNullable(owner); }
    public CommitmentConfirmationStatus status() { return status; }
    public Optional<LocalDate> dueDate() { return Optional.ofNullable(dueDate); }
    public boolean overdue() { return overdue; }
    public Instant recordedAt() { return recordedAt; }
    public Instant updatedAt() { return updatedAt; }
    public Optional<Instant> decidedAt() { return Optional.ofNullable(decidedAt); }
    public Optional<UUID> decidedByUserId() { return Optional.ofNullable(decidedByUserId); }
}
