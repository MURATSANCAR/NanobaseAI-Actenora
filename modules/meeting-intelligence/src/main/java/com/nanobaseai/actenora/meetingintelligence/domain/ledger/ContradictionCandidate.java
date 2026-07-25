package com.nanobaseai.actenora.meetingintelligence.domain.ledger;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * AI-proposed contradiction. Never mutates the ledger until human confirmation.
 */
public final class ContradictionCandidate {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID meetingOccurrenceId;
    private final UUID leftDecisionId;
    private final UUID rightDecisionId;
    private final String reason;
    private final BigDecimal confidence;
    private final ContradictionStatus status;
    private final Instant createdAt;
    private final Instant decidedAt;
    private final String decidedBy;

    private ContradictionCandidate(
            UUID id,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID leftDecisionId,
            UUID rightDecisionId,
            String reason,
            BigDecimal confidence,
            ContradictionStatus status,
            Instant createdAt,
            Instant decidedAt,
            String decidedBy
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.meetingOccurrenceId = Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        this.leftDecisionId = Objects.requireNonNull(leftDecisionId, "leftDecisionId");
        this.rightDecisionId = Objects.requireNonNull(rightDecisionId, "rightDecisionId");
        this.reason = requireText(reason, "reason");
        this.confidence = Objects.requireNonNull(confidence, "confidence");
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        if (leftDecisionId.equals(rightDecisionId)) {
            throw new IllegalArgumentException("contradiction decisions must differ");
        }
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.decidedAt = decidedAt;
        this.decidedBy = decidedBy;
    }

    public static ContradictionCandidate propose(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID leftDecisionId,
            UUID rightDecisionId,
            String reason,
            BigDecimal confidence,
            Instant now
    ) {
        return new ContradictionCandidate(
                UUID.randomUUID(),
                tenantId,
                meetingOccurrenceId,
                leftDecisionId,
                rightDecisionId,
                reason,
                confidence,
                ContradictionStatus.PENDING,
                now,
                null,
                null
        );
    }

    public static ContradictionCandidate rehydrate(
            UUID id,
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID leftDecisionId,
            UUID rightDecisionId,
            String reason,
            BigDecimal confidence,
            ContradictionStatus status,
            Instant createdAt,
            Instant decidedAt,
            String decidedBy
    ) {
        return new ContradictionCandidate(
                id, tenantId, meetingOccurrenceId, leftDecisionId, rightDecisionId,
                reason, confidence, status, createdAt, decidedAt, decidedBy
        );
    }

    public ContradictionCandidate confirm(String actor, Instant now) {
        requirePending();
        return new ContradictionCandidate(
                id, tenantId, meetingOccurrenceId, leftDecisionId, rightDecisionId,
                reason, confidence, ContradictionStatus.CONFIRMED, createdAt, now,
                Objects.requireNonNull(actor, "actor")
        );
    }

    public ContradictionCandidate reject(String actor, Instant now) {
        requirePending();
        return new ContradictionCandidate(
                id, tenantId, meetingOccurrenceId, leftDecisionId, rightDecisionId,
                reason, confidence, ContradictionStatus.REJECTED, createdAt, now,
                Objects.requireNonNull(actor, "actor")
        );
    }

    private void requirePending() {
        if (status != ContradictionStatus.PENDING) {
            throw new IllegalStateException("contradiction is already " + status);
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
    public UUID meetingOccurrenceId() { return meetingOccurrenceId; }
    public UUID leftDecisionId() { return leftDecisionId; }
    public UUID rightDecisionId() { return rightDecisionId; }
    public String reason() { return reason; }
    public BigDecimal confidence() { return confidence; }
    public ContradictionStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Optional<Instant> decidedAt() { return Optional.ofNullable(decidedAt); }
    public Optional<String> decidedBy() { return Optional.ofNullable(decidedBy); }
}
