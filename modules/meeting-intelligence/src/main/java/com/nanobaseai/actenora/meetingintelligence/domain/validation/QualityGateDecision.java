package com.nanobaseai.actenora.meetingintelligence.domain.validation;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Gate decision for a validation run. Human override is audited and never silently mutates the run.
 */
public final class QualityGateDecision {

    private final UUID id;
    private final UUID tenantId;
    private final UUID validationRunId;
    private final QualityGateOutcome outcome;
    private final boolean overridden;
    private final String overrideActor;
    private final String overrideReason;
    private final QualityGateOutcome originalOutcome;
    private final Instant decidedAt;

    private QualityGateDecision(
            UUID id,
            UUID tenantId,
            UUID validationRunId,
            QualityGateOutcome outcome,
            boolean overridden,
            String overrideActor,
            String overrideReason,
            QualityGateOutcome originalOutcome,
            Instant decidedAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.validationRunId = Objects.requireNonNull(validationRunId, "validationRunId");
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.overridden = overridden;
        this.overrideActor = overrideActor;
        this.overrideReason = overrideReason;
        this.originalOutcome = originalOutcome;
        this.decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
    }

    public static QualityGateDecision fromRun(ValidationRun run, Instant now) {
        return new QualityGateDecision(
                UUID.randomUUID(),
                run.tenantId(),
                run.id(),
                run.computedOutcome(),
                false,
                null,
                null,
                null,
                now
        );
    }

    public static QualityGateDecision rehydrate(
            UUID id,
            UUID tenantId,
            UUID validationRunId,
            QualityGateOutcome outcome,
            boolean overridden,
            String overrideActor,
            String overrideReason,
            QualityGateOutcome originalOutcome,
            Instant decidedAt
    ) {
        return new QualityGateDecision(
                id,
                tenantId,
                validationRunId,
                outcome,
                overridden,
                overrideActor,
                overrideReason,
                originalOutcome,
                decidedAt
        );
    }

    public QualityGateDecision override(String actor, String reason, QualityGateOutcome newOutcome, Instant now) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(newOutcome, "newOutcome");
        if (actor.isBlank() || reason.isBlank()) {
            throw new IllegalArgumentException("override actor and reason must not be blank");
        }
        if (overridden) {
            throw new IllegalStateException("quality gate decision already overridden");
        }
        if (newOutcome == outcome) {
            throw new IllegalArgumentException("override outcome must differ from current outcome");
        }
        return new QualityGateDecision(
                id,
                tenantId,
                validationRunId,
                newOutcome,
                true,
                actor,
                reason,
                outcome,
                now
        );
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

    public QualityGateOutcome outcome() {
        return outcome;
    }

    public boolean overridden() {
        return overridden;
    }

    public Optional<String> overrideActor() {
        return Optional.ofNullable(overrideActor);
    }

    public Optional<String> overrideReason() {
        return Optional.ofNullable(overrideReason);
    }

    public Optional<QualityGateOutcome> originalOutcome() {
        return Optional.ofNullable(originalOutcome);
    }

    public Instant decidedAt() {
        return decidedAt;
    }

    public boolean allowsOfficialDraft() {
        return outcome == QualityGateOutcome.PASSED || outcome == QualityGateOutcome.PASSED_WITH_WARNINGS;
    }
}
