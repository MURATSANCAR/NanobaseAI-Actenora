package com.nanobaseai.actenora.meetingintelligence.domain.model;

import com.nanobaseai.actenora.meetingintelligence.domain.exception.AiConfidenceIsNotApprovalException;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.TenantIsolationViolationException;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Decision {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID noteId;
    private final UUID noteVersionId;
    private String text;
    private UUID supersedesDecisionId;
    private UUID supersededByDecisionId;
    private boolean requiresManualReview;
    private final Double aiConfidence;
    private HumanApprovalStatus humanApprovalStatus;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private Decision(
            UUID id,
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            String text,
            UUID supersedesDecisionId,
            UUID supersededByDecisionId,
            boolean requiresManualReview,
            Double aiConfidence,
            HumanApprovalStatus humanApprovalStatus,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.noteId = Objects.requireNonNull(noteId, "noteId");
        this.noteVersionId = Objects.requireNonNull(noteVersionId, "noteVersionId");
        this.text = requireText(text, "text");
        this.supersedesDecisionId = supersedesDecisionId;
        this.supersededByDecisionId = supersededByDecisionId;
        this.requiresManualReview = requiresManualReview;
        this.aiConfidence = aiConfidence;
        this.humanApprovalStatus = Objects.requireNonNull(humanApprovalStatus, "humanApprovalStatus");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    public static Decision createFromMapping(
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            String text,
            boolean requiresManualReview,
            Double aiConfidence,
            Instant now
    ) {
        return new Decision(
                UUID.randomUUID(),
                tenantId,
                noteId,
                noteVersionId,
                text,
                null,
                null,
                requiresManualReview,
                aiConfidence,
                HumanApprovalStatus.NONE,
                now,
                now,
                0L
        );
    }

    public static Decision rehydrate(
            UUID id,
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            String text,
            UUID supersedesDecisionId,
            UUID supersededByDecisionId,
            boolean requiresManualReview,
            Double aiConfidence,
            HumanApprovalStatus humanApprovalStatus,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        return new Decision(
                id, tenantId, noteId, noteVersionId, text, supersedesDecisionId, supersededByDecisionId,
                requiresManualReview, aiConfidence, humanApprovalStatus, createdAt, updatedAt, version
        );
    }

    public void updateText(String text, long expectedVersion, Instant now) {
        assertVersion(expectedVersion);
        this.text = requireText(text, "text");
        touch(expectedVersion, now);
    }

    /**
     * This decision supersedes {@code older}. Directed relationship.
     */
    public void supersede(Decision older, long expectedVersion, Instant now) {
        assertVersion(expectedVersion);
        Objects.requireNonNull(older, "older");
        older.assertTenant(this.tenantId);
        if (older.id.equals(this.id)) {
            throw new IllegalArgumentException("decision cannot supersede itself");
        }
        this.supersedesDecisionId = older.id;
        older.markSupersededBy(this.id, now);
        touch(expectedVersion, now);
    }

    private void markSupersededBy(UUID newerDecisionId, Instant now) {
        this.supersededByDecisionId = newerDecisionId;
        this.updatedAt = now;
        this.version = this.version + 1;
    }

    public void setHumanApproval(HumanApprovalStatus status, long expectedVersion, Instant now) {
        assertVersion(expectedVersion);
        Objects.requireNonNull(status, "status");
        if (status == HumanApprovalStatus.NONE) {
            throw new IllegalArgumentException("human approval status must be APPROVED or REJECTED");
        }
        this.humanApprovalStatus = status;
        touch(expectedVersion, now);
    }

    /**
     * Guard: AI confidence must never be copied into humanApprovalStatus.
     */
    public void rejectAiConfidenceAsApproval(double ignoredConfidence) {
        throw new AiConfidenceIsNotApprovalException();
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

    private void touch(long expectedVersion, Instant now) {
        this.updatedAt = now;
        this.version = expectedVersion + 1;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public UUID noteId() { return noteId; }
    public UUID noteVersionId() { return noteVersionId; }
    public String text() { return text; }
    public UUID supersedesDecisionId() { return supersedesDecisionId; }
    public UUID supersededByDecisionId() { return supersededByDecisionId; }
    public boolean requiresManualReview() { return requiresManualReview; }
    public Double aiConfidence() { return aiConfidence; }
    public HumanApprovalStatus humanApprovalStatus() { return humanApprovalStatus; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
