package com.nanobaseai.actenora.meetingintelligence.domain.validation;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Human review work item created when the quality gate cannot auto-accept.
 */
public final class ManualReviewCase {

    private final UUID id;
    private final UUID tenantId;
    private final UUID validationRunId;
    private final UUID qualityGateDecisionId;
    private final UUID meetingOccurrenceId;
    private final String reason;
    private final ManualReviewStatus status;
    private final String resolvedBy;
    private final String resolutionNote;
    private final Instant createdAt;
    private final Instant resolvedAt;

    private ManualReviewCase(
            UUID id,
            UUID tenantId,
            UUID validationRunId,
            UUID qualityGateDecisionId,
            UUID meetingOccurrenceId,
            String reason,
            ManualReviewStatus status,
            String resolvedBy,
            String resolutionNote,
            Instant createdAt,
            Instant resolvedAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.validationRunId = Objects.requireNonNull(validationRunId, "validationRunId");
        this.qualityGateDecisionId = Objects.requireNonNull(qualityGateDecisionId, "qualityGateDecisionId");
        this.meetingOccurrenceId = Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.status = Objects.requireNonNull(status, "status");
        this.resolvedBy = resolvedBy;
        this.resolutionNote = resolutionNote;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.resolvedAt = resolvedAt;
    }

    public static ManualReviewCase open(
            UUID tenantId,
            UUID validationRunId,
            UUID qualityGateDecisionId,
            UUID meetingOccurrenceId,
            String reason,
            Instant now
    ) {
        return new ManualReviewCase(
                UUID.randomUUID(),
                tenantId,
                validationRunId,
                qualityGateDecisionId,
                meetingOccurrenceId,
                reason,
                ManualReviewStatus.OPEN,
                null,
                null,
                now,
                null
        );
    }

    public static ManualReviewCase rehydrate(
            UUID id,
            UUID tenantId,
            UUID validationRunId,
            UUID qualityGateDecisionId,
            UUID meetingOccurrenceId,
            String reason,
            ManualReviewStatus status,
            String resolvedBy,
            String resolutionNote,
            Instant createdAt,
            Instant resolvedAt
    ) {
        return new ManualReviewCase(
                id,
                tenantId,
                validationRunId,
                qualityGateDecisionId,
                meetingOccurrenceId,
                reason,
                status,
                resolvedBy,
                resolutionNote,
                createdAt,
                resolvedAt
        );
    }

    public ManualReviewCase resolveOverride(String actor, String note, Instant now) {
        return resolve(actor, note, ManualReviewStatus.RESOLVED_OVERRIDE, now);
    }

    public ManualReviewCase resolveRejected(String actor, String note, Instant now) {
        return resolve(actor, note, ManualReviewStatus.RESOLVED_REJECTED, now);
    }

    public ManualReviewCase dismiss(String actor, String note, Instant now) {
        return resolve(actor, note, ManualReviewStatus.DISMISSED, now);
    }

    private ManualReviewCase resolve(String actor, String note, ManualReviewStatus newStatus, Instant now) {
        requireOpen();
        Objects.requireNonNull(actor, "actor");
        if (actor.isBlank()) {
            throw new IllegalArgumentException("actor must not be blank");
        }
        return new ManualReviewCase(
                id,
                tenantId,
                validationRunId,
                qualityGateDecisionId,
                meetingOccurrenceId,
                reason,
                newStatus,
                actor,
                note,
                createdAt,
                now
        );
    }

    private void requireOpen() {
        if (status != ManualReviewStatus.OPEN) {
            throw new IllegalStateException("manual review case is already " + status);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID validationRunId() {
        return validationRunId;
    }

    public UUID qualityGateDecisionId() {
        return qualityGateDecisionId;
    }

    public UUID meetingOccurrenceId() {
        return meetingOccurrenceId;
    }

    public String reason() {
        return reason;
    }

    public ManualReviewStatus status() {
        return status;
    }

    public Optional<String> resolvedBy() {
        return Optional.ofNullable(resolvedBy);
    }

    public Optional<String> resolutionNote() {
        return Optional.ofNullable(resolutionNote);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<Instant> resolvedAt() {
        return Optional.ofNullable(resolvedAt);
    }
}
