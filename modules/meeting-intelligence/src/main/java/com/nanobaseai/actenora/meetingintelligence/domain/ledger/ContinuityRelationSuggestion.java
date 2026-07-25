package com.nanobaseai.actenora.meetingintelligence.domain.ledger;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * AI continuity relation suggestion (same-series / same-business-context / follow-up).
 * Approval required before projection materializes the link.
 */
public final class ContinuityRelationSuggestion {

    public enum ProposedRelation {
        SAME_SERIES,
        SAME_BUSINESS_CONTEXT,
        FOLLOW_UP
    }

    private final UUID id;
    private final TenantId tenantId;
    private final UUID sourceOccurrenceId;
    private final UUID targetOccurrenceId;
    private final ProposedRelation proposedRelation;
    private final BigDecimal confidence;
    private final String reason;
    private final ContinuitySuggestionStatus status;
    private final Instant createdAt;
    private final Instant decidedAt;
    private final String decidedBy;

    private ContinuityRelationSuggestion(
            UUID id,
            TenantId tenantId,
            UUID sourceOccurrenceId,
            UUID targetOccurrenceId,
            ProposedRelation proposedRelation,
            BigDecimal confidence,
            String reason,
            ContinuitySuggestionStatus status,
            Instant createdAt,
            Instant decidedAt,
            String decidedBy
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.sourceOccurrenceId = Objects.requireNonNull(sourceOccurrenceId, "sourceOccurrenceId");
        this.targetOccurrenceId = Objects.requireNonNull(targetOccurrenceId, "targetOccurrenceId");
        this.proposedRelation = Objects.requireNonNull(proposedRelation, "proposedRelation");
        this.confidence = Objects.requireNonNull(confidence, "confidence");
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        if (sourceOccurrenceId.equals(targetOccurrenceId)) {
            throw new IllegalArgumentException("source and target must differ");
        }
        this.reason = requireText(reason, "reason");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.decidedAt = decidedAt;
        this.decidedBy = decidedBy;
    }

    public static ContinuityRelationSuggestion propose(
            TenantId tenantId,
            UUID sourceOccurrenceId,
            UUID targetOccurrenceId,
            ProposedRelation proposedRelation,
            BigDecimal confidence,
            String reason,
            Instant now
    ) {
        return new ContinuityRelationSuggestion(
                UUID.randomUUID(),
                tenantId,
                sourceOccurrenceId,
                targetOccurrenceId,
                proposedRelation,
                confidence,
                reason,
                ContinuitySuggestionStatus.PENDING,
                now,
                null,
                null
        );
    }

    public static ContinuityRelationSuggestion rehydrate(
            UUID id,
            TenantId tenantId,
            UUID sourceOccurrenceId,
            UUID targetOccurrenceId,
            ProposedRelation proposedRelation,
            BigDecimal confidence,
            String reason,
            ContinuitySuggestionStatus status,
            Instant createdAt,
            Instant decidedAt,
            String decidedBy
    ) {
        return new ContinuityRelationSuggestion(
                id, tenantId, sourceOccurrenceId, targetOccurrenceId, proposedRelation,
                confidence, reason, status, createdAt, decidedAt, decidedBy
        );
    }

    public ContinuityRelationSuggestion approve(String actor, Instant now) {
        requirePending();
        return decide(ContinuitySuggestionStatus.APPROVED, actor, now);
    }

    public ContinuityRelationSuggestion reject(String actor, Instant now) {
        requirePending();
        return decide(ContinuitySuggestionStatus.REJECTED, actor, now);
    }

    private ContinuityRelationSuggestion decide(ContinuitySuggestionStatus next, String actor, Instant now) {
        return new ContinuityRelationSuggestion(
                id, tenantId, sourceOccurrenceId, targetOccurrenceId, proposedRelation,
                confidence, reason, next, createdAt, now, Objects.requireNonNull(actor, "actor")
        );
    }

    private void requirePending() {
        if (status != ContinuitySuggestionStatus.PENDING) {
            throw new IllegalStateException("suggestion is already " + status);
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
    public UUID sourceOccurrenceId() { return sourceOccurrenceId; }
    public UUID targetOccurrenceId() { return targetOccurrenceId; }
    public ProposedRelation proposedRelation() { return proposedRelation; }
    public BigDecimal confidence() { return confidence; }
    public String reason() { return reason; }
    public ContinuitySuggestionStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Optional<Instant> decidedAt() { return Optional.ofNullable(decidedAt); }
    public Optional<String> decidedBy() { return Optional.ofNullable(decidedBy); }
}
