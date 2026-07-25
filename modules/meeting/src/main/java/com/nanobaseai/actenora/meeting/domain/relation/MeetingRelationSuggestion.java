package com.nanobaseai.actenora.meeting.domain.relation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * AI-proposed meeting relation. Never creates a {@link MeetingRelation} by itself —
 * approval is required and produces an AI_SUGGESTED relation.
 */
public final class MeetingRelationSuggestion {

    private final UUID id;
    private final UUID tenantId;
    private final UUID sourceOccurrenceId;
    private final UUID targetOccurrenceId;
    private final RelationType proposedType;
    private final BigDecimal confidence;
    private final String reason;
    private final SuggestionStatus status;
    private final Instant createdAt;
    private final Instant decidedAt;
    private final String decidedBy;

    private MeetingRelationSuggestion(
            UUID id,
            UUID tenantId,
            UUID sourceOccurrenceId,
            UUID targetOccurrenceId,
            RelationType proposedType,
            BigDecimal confidence,
            String reason,
            SuggestionStatus status,
            Instant createdAt,
            Instant decidedAt,
            String decidedBy
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.sourceOccurrenceId = Objects.requireNonNull(sourceOccurrenceId, "sourceOccurrenceId");
        this.targetOccurrenceId = Objects.requireNonNull(targetOccurrenceId, "targetOccurrenceId");
        this.proposedType = Objects.requireNonNull(proposedType, "proposedType");
        this.confidence = Objects.requireNonNull(confidence, "confidence");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.decidedAt = decidedAt;
        this.decidedBy = decidedBy;
        if (sourceOccurrenceId.equals(targetOccurrenceId)) {
            throw new IllegalArgumentException("source and target occurrence must differ");
        }
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }

    public static MeetingRelationSuggestion propose(
            UUID tenantId,
            UUID sourceOccurrenceId,
            UUID targetOccurrenceId,
            RelationType proposedType,
            BigDecimal confidence,
            String reason,
            Instant now
    ) {
        return new MeetingRelationSuggestion(
                UUID.randomUUID(),
                tenantId,
                sourceOccurrenceId,
                targetOccurrenceId,
                proposedType,
                confidence,
                reason,
                SuggestionStatus.PENDING,
                now,
                null,
                null
        );
    }

    public static MeetingRelationSuggestion rehydrate(
            UUID id,
            UUID tenantId,
            UUID sourceOccurrenceId,
            UUID targetOccurrenceId,
            RelationType proposedType,
            BigDecimal confidence,
            String reason,
            SuggestionStatus status,
            Instant createdAt,
            Instant decidedAt,
            String decidedBy
    ) {
        return new MeetingRelationSuggestion(
                id,
                tenantId,
                sourceOccurrenceId,
                targetOccurrenceId,
                proposedType,
                confidence,
                reason,
                status,
                createdAt,
                decidedAt,
                decidedBy
        );
    }

    public MeetingRelationSuggestion approve(String actor, Instant now) {
        requirePending();
        return new MeetingRelationSuggestion(
                id,
                tenantId,
                sourceOccurrenceId,
                targetOccurrenceId,
                proposedType,
                confidence,
                reason,
                SuggestionStatus.APPROVED,
                createdAt,
                now,
                Objects.requireNonNull(actor, "actor")
        );
    }

    public MeetingRelationSuggestion reject(String actor, Instant now) {
        requirePending();
        return new MeetingRelationSuggestion(
                id,
                tenantId,
                sourceOccurrenceId,
                targetOccurrenceId,
                proposedType,
                confidence,
                reason,
                SuggestionStatus.REJECTED,
                createdAt,
                now,
                Objects.requireNonNull(actor, "actor")
        );
    }

    private void requirePending() {
        if (status != SuggestionStatus.PENDING) {
            throw new IllegalStateException("suggestion is already " + status);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID sourceOccurrenceId() {
        return sourceOccurrenceId;
    }

    public UUID targetOccurrenceId() {
        return targetOccurrenceId;
    }

    public RelationType proposedType() {
        return proposedType;
    }

    public BigDecimal confidence() {
        return confidence;
    }

    public String reason() {
        return reason;
    }

    public SuggestionStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant decidedAt() {
        return decidedAt;
    }

    public String decidedBy() {
        return decidedBy;
    }
}
