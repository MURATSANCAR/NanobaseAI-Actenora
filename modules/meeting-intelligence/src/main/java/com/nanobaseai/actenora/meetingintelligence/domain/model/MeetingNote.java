package com.nanobaseai.actenora.meetingintelligence.domain.model;

import com.nanobaseai.actenora.meetingintelligence.domain.exception.CorrectionReasonRequiredException;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.OptimisticLockConflictException;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.TenantIsolationViolationException;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate root for a meeting note. Mutable head pointer; content lives in immutable versions.
 */
public final class MeetingNote {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID meetingOccurrenceId;
    private UUID currentVersionId;
    private int currentVersionNumber;
    private NoteReviewStatus reviewStatus;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;

    private MeetingNote(
            UUID id,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID currentVersionId,
            int currentVersionNumber,
            NoteReviewStatus reviewStatus,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.meetingOccurrenceId = Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        this.currentVersionId = Objects.requireNonNull(currentVersionId, "currentVersionId");
        this.currentVersionNumber = currentVersionNumber;
        this.reviewStatus = Objects.requireNonNull(reviewStatus, "reviewStatus");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    public static MeetingNote create(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            NoteReviewStatus reviewStatus,
            Instant now
    ) {
        UUID noteId = UUID.randomUUID();
        return new MeetingNote(
                noteId,
                tenantId,
                meetingOccurrenceId,
                noteId, // placeholder until first version attached
                0,
                reviewStatus == null ? NoteReviewStatus.ACTIVE : reviewStatus,
                now,
                now,
                0L
        );
    }

    public static MeetingNote rehydrate(
            UUID id,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID currentVersionId,
            int currentVersionNumber,
            NoteReviewStatus reviewStatus,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        return new MeetingNote(
                id, tenantId, meetingOccurrenceId, currentVersionId, currentVersionNumber,
                reviewStatus, createdAt, updatedAt, version
        );
    }

    public MeetingNoteVersion attachInitialAiVersion(
            String executiveSummary,
            ModelPromptSchemaProvenance provenance,
            Instant now
    ) {
        if (currentVersionNumber != 0) {
            throw new IllegalStateException("initial version already attached");
        }
        MeetingNoteVersion versionEntity = MeetingNoteVersion.createAiMapped(
                tenantId, id, 1, executiveSummary, provenance, now
        );
        this.currentVersionId = versionEntity.id();
        this.currentVersionNumber = 1;
        this.updatedAt = now;
        return versionEntity;
    }

    public MeetingNoteVersion appendHumanEdit(
            String executiveSummary,
            String correctionReason,
            UUID actorUserId,
            ModelPromptSchemaProvenance carriedProvenance,
            long expectedVersion,
            Instant now
    ) {
        assertVersion(expectedVersion);
        if (correctionReason == null || correctionReason.isBlank()) {
            throw new CorrectionReasonRequiredException();
        }
        int next = currentVersionNumber + 1;
        MeetingNoteVersion versionEntity = MeetingNoteVersion.createHumanEdit(
                tenantId,
                id,
                next,
                executiveSummary,
                correctionReason,
                actorUserId,
                carriedProvenance,
                now
        );
        this.currentVersionId = versionEntity.id();
        this.currentVersionNumber = next;
        this.updatedAt = now;
        this.version = expectedVersion + 1;
        return versionEntity;
    }

    public void markManualReview(long expectedVersion, Instant now) {
        assertVersion(expectedVersion);
        this.reviewStatus = NoteReviewStatus.MANUAL_REVIEW;
        this.updatedAt = now;
        this.version = expectedVersion + 1;
    }

    public void markManualReviewWithoutLock(Instant now) {
        this.reviewStatus = NoteReviewStatus.MANUAL_REVIEW;
        this.updatedAt = now;
    }

    public void assertVersion(long expectedVersion) {
        if (this.version != expectedVersion) {
            throw new OptimisticLockConflictException(id, expectedVersion);
        }
    }

    /**
     * Bumps the aggregate optimistic lock without changing review posture (FAZ 18 approval transitions).
     */
    public void touchOptimisticLock(long expectedVersion, Instant now) {
        assertVersion(expectedVersion);
        this.updatedAt = now;
        this.version = expectedVersion + 1;
    }

    public void assertTenant(TenantId tenantId) {
        if (!this.tenantId.equals(tenantId)) {
            throw new TenantIsolationViolationException();
        }
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public UUID meetingOccurrenceId() { return meetingOccurrenceId; }
    public UUID currentVersionId() { return currentVersionId; }
    public int currentVersionNumber() { return currentVersionNumber; }
    public NoteReviewStatus reviewStatus() { return reviewStatus; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}
