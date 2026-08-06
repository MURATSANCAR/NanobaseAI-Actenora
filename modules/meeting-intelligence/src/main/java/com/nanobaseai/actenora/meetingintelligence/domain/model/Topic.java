package com.nanobaseai.actenora.meetingintelligence.domain.model;

import com.nanobaseai.actenora.meetingintelligence.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.TenantIsolationViolationException;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Discussed topic (agenda item, rendered under "GÖRÜŞÜLEN KONULAR").
 * Standalone note sub-entity linked by noteId + noteVersionId; mirrors {@link ImportantFact}.
 */
public final class Topic {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID noteId;
    private final UUID noteVersionId;
    private String text;
    private boolean requiresManualReview;
    private final Double aiConfidence;
    private final HumanApprovalStatus humanApprovalStatus;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private Topic(
            UUID id,
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            String text,
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
        this.requiresManualReview = requiresManualReview;
        this.aiConfidence = aiConfidence;
        this.humanApprovalStatus = Objects.requireNonNull(humanApprovalStatus, "humanApprovalStatus");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    public static Topic createFromMapping(
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            String text,
            boolean requiresManualReview,
            Double aiConfidence,
            Instant now
    ) {
        return new Topic(
                UUID.randomUUID(),
                tenantId,
                noteId,
                noteVersionId,
                text,
                requiresManualReview,
                aiConfidence,
                HumanApprovalStatus.NONE,
                now,
                now,
                0L
        );
    }

    public static Topic rehydrate(
            UUID id,
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            String text,
            boolean requiresManualReview,
            Double aiConfidence,
            HumanApprovalStatus humanApprovalStatus,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        return new Topic(
                id, tenantId, noteId, noteVersionId, text, requiresManualReview,
                aiConfidence, humanApprovalStatus, createdAt, updatedAt, version
        );
    }

    public void updateText(String text, long expectedVersion, Instant now) {
        assertVersion(expectedVersion);
        this.text = requireText(text, "text");
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

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public UUID noteId() { return noteId; }
    public UUID noteVersionId() { return noteVersionId; }
    public String text() { return text; }
    public boolean requiresManualReview() { return requiresManualReview; }
    public Double aiConfidence() { return aiConfidence; }
    public HumanApprovalStatus humanApprovalStatus() { return humanApprovalStatus; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
