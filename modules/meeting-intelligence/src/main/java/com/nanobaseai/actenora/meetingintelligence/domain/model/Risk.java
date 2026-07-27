package com.nanobaseai.actenora.meetingintelligence.domain.model;

import com.nanobaseai.actenora.meetingintelligence.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.TenantIsolationViolationException;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Risk {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID noteId;
    private final UUID noteVersionId;
    private String text;
    private boolean requiresManualReview;
    private final Double aiConfidence;
    private HumanApprovalStatus humanApprovalStatus;
    private final String likelihood;
    private final String mitigation;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private Risk(
            UUID id,
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            String text,
            boolean requiresManualReview,
            Double aiConfidence,
            HumanApprovalStatus humanApprovalStatus,
            String likelihood,
            String mitigation,
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
        this.likelihood = blankToNull(likelihood);
        this.mitigation = blankToNull(mitigation);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    public static Risk createFromMapping(
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            String text,
            boolean requiresManualReview,
            Double aiConfidence,
            Instant now
    ) {
        return createFromMapping(tenantId, noteId, noteVersionId, text, requiresManualReview, aiConfidence, null, null, now);
    }

    public static Risk createFromMapping(
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            String text,
            boolean requiresManualReview,
            Double aiConfidence,
            String likelihood,
            String mitigation,
            Instant now
    ) {
        return new Risk(
                UUID.randomUUID(),
                tenantId,
                noteId,
                noteVersionId,
                text,
                requiresManualReview,
                aiConfidence,
                HumanApprovalStatus.NONE,
                likelihood,
                mitigation,
                now,
                now,
                0L
        );
    }

    public static Risk rehydrate(
            UUID id,
            TenantId tenantId,
            UUID noteId,
            UUID noteVersionId,
            String text,
            boolean requiresManualReview,
            Double aiConfidence,
            HumanApprovalStatus humanApprovalStatus,
            String likelihood,
            String mitigation,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        return new Risk(
                id, tenantId, noteId, noteVersionId, text, requiresManualReview,
                aiConfidence, humanApprovalStatus, likelihood, mitigation, createdAt, updatedAt, version
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

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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
    public String likelihood() { return likelihood; }
    public String mitigation() { return mitigation; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
