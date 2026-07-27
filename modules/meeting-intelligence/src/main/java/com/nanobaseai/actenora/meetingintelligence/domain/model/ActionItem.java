package com.nanobaseai.actenora.meetingintelligence.domain.model;

import com.nanobaseai.actenora.meetingintelligence.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.TenantIsolationViolationException;
import com.nanobaseai.actenora.meetingintelligence.domain.service.ActionItemStateMachine;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class ActionItem {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID noteId;
    private final UUID noteVersionId;
    private String text;
    private String owner;
    private LocalDate dueDate;
    private ActionItemStatus status;
    private boolean requiresManualReview;
    private final Double aiConfidence;
    private HumanApprovalStatus humanApprovalStatus;
    private final String ownerType;
    private final String priority;
    private final String relativeDate;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private ActionItem(
            UUID id,
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            String text,
            String owner,
            LocalDate dueDate,
            ActionItemStatus status,
            boolean requiresManualReview,
            Double aiConfidence,
            HumanApprovalStatus humanApprovalStatus,
            String ownerType,
            String priority,
            String relativeDate,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.noteId = Objects.requireNonNull(noteId, "noteId");
        this.noteVersionId = Objects.requireNonNull(noteVersionId, "noteVersionId");
        this.text = requireText(text, "text");
        this.owner = blankToNull(owner);
        this.dueDate = dueDate;
        this.status = Objects.requireNonNull(status, "status");
        this.requiresManualReview = requiresManualReview;
        this.aiConfidence = aiConfidence;
        this.humanApprovalStatus = Objects.requireNonNull(humanApprovalStatus, "humanApprovalStatus");
        this.ownerType = blankToNull(ownerType);
        this.priority = blankToNull(priority);
        this.relativeDate = blankToNull(relativeDate);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    public static ActionItem createFromMapping(
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            String text,
            String owner,
            LocalDate dueDate,
            boolean requiresManualReview,
            Double aiConfidence,
            Instant now
    ) {
        return createFromMapping(
                tenantId, noteId, noteVersionId, text, owner, dueDate,
                requiresManualReview, aiConfidence, null, null, null, now
        );
    }

    public static ActionItem createFromMapping(
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            String text,
            String owner,
            LocalDate dueDate,
            boolean requiresManualReview,
            Double aiConfidence,
            String ownerType,
            String priority,
            String relativeDate,
            Instant now
    ) {
        return new ActionItem(
                UUID.randomUUID(),
                tenantId,
                noteId,
                noteVersionId,
                text,
                owner,
                dueDate,
                ActionItemStatus.OPEN,
                requiresManualReview,
                aiConfidence,
                HumanApprovalStatus.NONE,
                ownerType,
                priority,
                relativeDate,
                now,
                now,
                0L
        );
    }

    public static ActionItem rehydrate(
            UUID id,
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            String text,
            String owner,
            LocalDate dueDate,
            ActionItemStatus status,
            boolean requiresManualReview,
            Double aiConfidence,
            HumanApprovalStatus humanApprovalStatus,
            String ownerType,
            String priority,
            String relativeDate,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        return new ActionItem(
                id, tenantId, noteId, noteVersionId, text, owner, dueDate, status,
                requiresManualReview, aiConfidence, humanApprovalStatus,
                ownerType, priority, relativeDate, createdAt, updatedAt, version
        );
    }

    public void update(
            String text,
            String owner,
            LocalDate dueDate,
            long expectedVersion,
            Instant now
    ) {
        assertVersion(expectedVersion);
        if (text != null) {
            this.text = requireText(text, "text");
        }
        if (owner != null) {
            this.owner = blankToNull(owner);
        }
        if (dueDate != null) {
            this.dueDate = dueDate;
        }
        touch(expectedVersion, now);
    }

    public void transitionTo(ActionItemStatus target, long expectedVersion, Instant now) {
        assertVersion(expectedVersion);
        ActionItemStateMachine.assertTransition(this.status, target);
        this.status = target;
        touch(expectedVersion, now);
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
    public LocalDate dueDate() { return dueDate; }
    public ActionItemStatus status() { return status; }
    public boolean requiresManualReview() { return requiresManualReview; }
    public Double aiConfidence() { return aiConfidence; }
    public HumanApprovalStatus humanApprovalStatus() { return humanApprovalStatus; }
    public String ownerType() { return ownerType; }
    public String priority() { return priority; }
    public String relativeDate() { return relativeDate; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
