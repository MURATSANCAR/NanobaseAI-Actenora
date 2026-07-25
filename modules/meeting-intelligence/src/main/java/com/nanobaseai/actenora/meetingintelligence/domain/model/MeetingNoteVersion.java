package com.nanobaseai.actenora.meetingintelligence.domain.model;

import com.nanobaseai.actenora.meetingintelligence.domain.exception.NoteVersionImmutableException;
import com.nanobaseai.actenora.meetingintelligence.domain.exception.TenantIsolationViolationException;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Content-immutable snapshot of a meeting note. Edits create a new version.
 * Approval lifecycle status may transition without rewriting content (FAZ 18).
 */
public final class MeetingNoteVersion {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID noteId;
    private final int versionNumber;
    private final String executiveSummary;
    private final NoteVersionSource source;
    private final ModelPromptSchemaProvenance provenance;
    private final String correctionReason;
    private final UUID createdByUserId;
    private final Instant createdAt;
    private MeetingNoteStatus approvalStatus;

    private MeetingNoteVersion(
            UUID id,
            TenantId tenantId,
            UUID noteId,
            int versionNumber,
            String executiveSummary,
            NoteVersionSource source,
            ModelPromptSchemaProvenance provenance,
            String correctionReason,
            UUID createdByUserId,
            Instant createdAt,
            MeetingNoteStatus approvalStatus
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.noteId = Objects.requireNonNull(noteId, "noteId");
        if (versionNumber < 1) {
            throw new IllegalArgumentException("versionNumber must be >= 1");
        }
        this.versionNumber = versionNumber;
        this.executiveSummary = requireText(executiveSummary, "executiveSummary");
        this.source = Objects.requireNonNull(source, "source");
        this.provenance = provenance;
        this.correctionReason = blankToNull(correctionReason);
        this.createdByUserId = createdByUserId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.approvalStatus = Objects.requireNonNull(approvalStatus, "approvalStatus");
    }

    public static MeetingNoteVersion createAiMapped(
            TenantId tenantId,
            UUID noteId,
            int versionNumber,
            String executiveSummary,
            ModelPromptSchemaProvenance provenance,
            Instant now
    ) {
        Objects.requireNonNull(provenance, "provenance");
        return new MeetingNoteVersion(
                UUID.randomUUID(),
                tenantId,
                noteId,
                versionNumber,
                executiveSummary,
                NoteVersionSource.AI_MAPPING,
                provenance,
                null,
                null,
                now,
                MeetingNoteStatus.DRAFT
        );
    }

    public static MeetingNoteVersion createHumanEdit(
            TenantId tenantId,
            UUID noteId,
            int versionNumber,
            String executiveSummary,
            String correctionReason,
            UUID createdByUserId,
            ModelPromptSchemaProvenance carriedProvenance,
            Instant now
    ) {
        String reason = requireText(correctionReason, "correctionReason");
        Objects.requireNonNull(createdByUserId, "createdByUserId");
        return new MeetingNoteVersion(
                UUID.randomUUID(),
                tenantId,
                noteId,
                versionNumber,
                executiveSummary,
                NoteVersionSource.HUMAN_EDIT,
                carriedProvenance,
                reason,
                createdByUserId,
                now,
                MeetingNoteStatus.DRAFT
        );
    }

    public static MeetingNoteVersion rehydrate(
            UUID id,
            TenantId tenantId,
            UUID noteId,
            int versionNumber,
            String executiveSummary,
            NoteVersionSource source,
            ModelPromptSchemaProvenance provenance,
            String correctionReason,
            UUID createdByUserId,
            Instant createdAt,
            MeetingNoteStatus approvalStatus
    ) {
        return new MeetingNoteVersion(
                id, tenantId, noteId, versionNumber, executiveSummary, source,
                provenance, correctionReason, createdByUserId, createdAt,
                approvalStatus == null ? MeetingNoteStatus.DRAFT : approvalStatus
        );
    }

    /**
     * Backward-compatible rehydrate defaulting approval status to DRAFT.
     */
    public static MeetingNoteVersion rehydrate(
            UUID id,
            TenantId tenantId,
            UUID noteId,
            int versionNumber,
            String executiveSummary,
            NoteVersionSource source,
            ModelPromptSchemaProvenance provenance,
            String correctionReason,
            UUID createdByUserId,
            Instant createdAt
    ) {
        return rehydrate(
                id, tenantId, noteId, versionNumber, executiveSummary, source,
                provenance, correctionReason, createdByUserId, createdAt, MeetingNoteStatus.DRAFT
        );
    }

    /**
     * Content is immutable — any content mutation attempt fails closed.
     */
    public void assertImmutable() {
        throw new NoteVersionImmutableException();
    }

    public void transitionApprovalStatus(MeetingNoteStatus target) {
        MeetingNoteStateMachine.assertTransition(approvalStatus, target);
        this.approvalStatus = target;
    }

    public boolean isApproved() {
        return approvalStatus == MeetingNoteStatus.APPROVED;
    }

    public boolean isContentLocked() {
        return approvalStatus == MeetingNoteStatus.APPROVED
                || approvalStatus == MeetingNoteStatus.REJECTED
                || approvalStatus == MeetingNoteStatus.SUPERSEDED
                || approvalStatus == MeetingNoteStatus.PENDING_APPROVAL;
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
    public int versionNumber() { return versionNumber; }
    public String executiveSummary() { return executiveSummary; }
    public NoteVersionSource source() { return source; }
    public ModelPromptSchemaProvenance provenance() { return provenance; }
    public String correctionReason() { return correctionReason; }
    public UUID createdByUserId() { return createdByUserId; }
    public Instant createdAt() { return createdAt; }
    public MeetingNoteStatus approvalStatus() { return approvalStatus; }
}
