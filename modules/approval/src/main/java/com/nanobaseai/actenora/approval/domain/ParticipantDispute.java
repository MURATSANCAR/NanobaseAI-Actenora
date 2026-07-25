package com.nanobaseai.actenora.approval.domain;

import com.nanobaseai.actenora.approval.api.ApprovalSubjectType;

import com.nanobaseai.actenora.approval.domain.exception.SilentOverwriteForbiddenException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Participant-raised correction proposal. Acceptance never mutates the subject in place —
 * callers must open a new draft version with the proposed content.
 */
public final class ParticipantDispute {

    private final UUID id;
    private final UUID tenantId;
    private final UUID subjectId;
    private final ApprovalSubjectType subjectType;
    private final String participantId;
    private final String proposedContent;
    private final String reason;
    private final DisputeStatus status;
    private final Instant createdAt;
    private final Instant resolvedAt;
    private final String resolvedBy;

    private ParticipantDispute(
            UUID id,
            UUID tenantId,
            UUID subjectId,
            ApprovalSubjectType subjectType,
            String participantId,
            String proposedContent,
            String reason,
            DisputeStatus status,
            Instant createdAt,
            Instant resolvedAt,
            String resolvedBy
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.subjectType = Objects.requireNonNull(subjectType, "subjectType");
        this.participantId = requireText(participantId, "participantId");
        this.proposedContent = requireText(proposedContent, "proposedContent");
        this.reason = requireText(reason, "reason");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.resolvedAt = resolvedAt;
        this.resolvedBy = resolvedBy;
    }

    public static ParticipantDispute raise(
            UUID tenantId,
            UUID subjectId,
            ApprovalSubjectType subjectType,
            String participantId,
            String proposedContent,
            String reason,
            Instant now
    ) {
        return new ParticipantDispute(
                UUID.randomUUID(),
                tenantId,
                subjectId,
                subjectType,
                participantId,
                proposedContent,
                reason,
                DisputeStatus.OPEN,
                now,
                null,
                null
        );
    }

    public static ParticipantDispute rehydrate(
            UUID id,
            UUID tenantId,
            UUID subjectId,
            ApprovalSubjectType subjectType,
            String participantId,
            String proposedContent,
            String reason,
            DisputeStatus status,
            Instant createdAt,
            Instant resolvedAt,
            String resolvedBy
    ) {
        return new ParticipantDispute(
                id, tenantId, subjectId, subjectType, participantId, proposedContent, reason,
                status, createdAt, resolvedAt, resolvedBy
        );
    }

    /**
     * Accepts the dispute as a correction proposal. Does not overwrite the subject.
     *
     * @return proposed content that must be applied via a new draft version
     */
    public AcceptedCorrection accept(String resolverId, Instant now) {
        requireOpen();
        ParticipantDispute accepted = new ParticipantDispute(
                id, tenantId, subjectId, subjectType, participantId, proposedContent, reason,
                DisputeStatus.ACCEPTED, createdAt, now, requireText(resolverId, "resolverId")
        );
        return new AcceptedCorrection(accepted, proposedContent);
    }

    public ParticipantDispute reject(String resolverId, Instant now) {
        requireOpen();
        return new ParticipantDispute(
                id, tenantId, subjectId, subjectType, participantId, proposedContent, reason,
                DisputeStatus.REJECTED, createdAt, now, requireText(resolverId, "resolverId")
        );
    }

    /**
     * Explicit guard — there is no in-place apply API on purpose.
     */
    public void applySilently() {
        throw new SilentOverwriteForbiddenException();
    }

    private void requireOpen() {
        if (status != DisputeStatus.OPEN) {
            throw new IllegalStateException("dispute is already " + status);
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID subjectId() {
        return subjectId;
    }

    public ApprovalSubjectType subjectType() {
        return subjectType;
    }

    public String participantId() {
        return participantId;
    }

    public String proposedContent() {
        return proposedContent;
    }

    public String reason() {
        return reason;
    }

    public DisputeStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant resolvedAt() {
        return resolvedAt;
    }

    public String resolvedBy() {
        return resolvedBy;
    }

    /**
     * Result of accepting a dispute — carries proposed content for a new draft, never an in-place write.
     */
    public record AcceptedCorrection(ParticipantDispute dispute, String proposedContentForNewDraft) {
        public AcceptedCorrection {
            Objects.requireNonNull(dispute, "dispute");
            Objects.requireNonNull(proposedContentForNewDraft, "proposedContentForNewDraft");
        }
    }
}
