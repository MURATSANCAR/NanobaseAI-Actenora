package com.nanobaseai.actenora.meetingintelligence.domain.model;

import com.nanobaseai.actenora.meetingintelligence.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.TenantIsolationViolationException;
import com.nanobaseai.actenora.meetingintelligence.domain.service.CommitmentConfirmationStateMachine;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Commitment {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID noteId;
    private final UUID noteVersionId;
    private String text;
    private String owner;
    private CommitmentConfirmationStatus confirmationStatus;
    private boolean requiresManualReview;
    private final Double aiConfidence;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant decidedAt;
    private UUID decidedByUserId;
    private long version;

    private Commitment(
            UUID id,
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            String text,
            String owner,
            CommitmentConfirmationStatus confirmationStatus,
            boolean requiresManualReview,
            Double aiConfidence,
            Instant createdAt,
            Instant updatedAt,
            Instant decidedAt,
            UUID decidedByUserId,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.noteId = Objects.requireNonNull(noteId, "noteId");
        this.noteVersionId = Objects.requireNonNull(noteVersionId, "noteVersionId");
        this.text = requireText(text, "text");
        this.owner = blankToNull(owner);
        this.confirmationStatus = Objects.requireNonNull(confirmationStatus, "confirmationStatus");
        this.requiresManualReview = requiresManualReview;
        this.aiConfidence = aiConfidence;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.decidedAt = decidedAt;
        this.decidedByUserId = decidedByUserId;
        this.version = version;
    }

    public static Commitment createFromMapping(
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            String text,
            String owner,
            boolean requiresManualReview,
            Double aiConfidence,
            Instant now
    ) {
        return new Commitment(
                UUID.randomUUID(),
                tenantId,
                noteId,
                noteVersionId,
                text,
                owner,
                CommitmentConfirmationStatus.PENDING_CONFIRMATION,
                requiresManualReview,
                aiConfidence,
                now,
                now,
                null,
                null,
                0L
        );
    }

    public static Commitment rehydrate(
            UUID id,
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            String text,
            String owner,
            CommitmentConfirmationStatus confirmationStatus,
            boolean requiresManualReview,
            Double aiConfidence,
            Instant createdAt,
            Instant updatedAt,
            Instant decidedAt,
            UUID decidedByUserId,
            long version
    ) {
        return new Commitment(
                id, tenantId, noteId, noteVersionId, text, owner, confirmationStatus,
                requiresManualReview, aiConfidence, createdAt, updatedAt, decidedAt, decidedByUserId, version
        );
    }

    public void approve(UUID actorUserId, long expectedVersion, Instant now) {
        transition(CommitmentConfirmationStatus.CONFIRMED, actorUserId, expectedVersion, now);
    }

    public void reject(UUID actorUserId, long expectedVersion, Instant now) {
        transition(CommitmentConfirmationStatus.REJECTED, actorUserId, expectedVersion, now);
    }

    private void transition(
            CommitmentConfirmationStatus target,
            UUID actorUserId,
            long expectedVersion,
            Instant now
    ) {
        assertVersion(expectedVersion);
        Objects.requireNonNull(actorUserId, "actorUserId");
        CommitmentConfirmationStateMachine.assertTransition(this.confirmationStatus, target);
        this.confirmationStatus = target;
        this.decidedAt = now;
        this.decidedByUserId = actorUserId;
        this.updatedAt = now;
        this.version = expectedVersion + 1;
    }

    public void assertVersion(long expectedVersion) {
        if (this.version != expectedVersion) {
            throw new OptimisticLockConflictException(id, expectedVersion);
        }
    }

    public void assertTenant(TenantId tenantId) {
        if (!this.tenantId.equals(tenantId)) {
            throw new TenantIsolationViolationException();
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public UUID noteId() { return noteId; }
    public UUID noteVersionId() { return noteVersionId; }
    public String text() { return text; }
    public String owner() { return owner; }
    public CommitmentConfirmationStatus confirmationStatus() { return confirmationStatus; }
    public boolean requiresManualReview() { return requiresManualReview; }
    public Double aiConfidence() { return aiConfidence; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant decidedAt() { return decidedAt; }
    public UUID decidedByUserId() { return decidedByUserId; }
    public long version() { return version; }
}
