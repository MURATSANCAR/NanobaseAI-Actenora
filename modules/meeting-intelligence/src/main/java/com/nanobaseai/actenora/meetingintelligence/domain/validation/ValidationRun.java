package com.nanobaseai.actenora.meetingintelligence.domain.validation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One deterministic validation execution. Re-validation appends a new run; prior runs are never deleted.
 */
public final class ValidationRun {

    private final UUID id;
    private final UUID tenantId;
    private final UUID meetingOccurrenceId;
    private final UUID sourceExtractionId;
    private final List<ValidationRuleResult> ruleResults;
    private final QualityGateOutcome computedOutcome;
    private final String engineVersion;
    private final Instant createdAt;

    private ValidationRun(
            UUID id,
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID sourceExtractionId,
            List<ValidationRuleResult> ruleResults,
            QualityGateOutcome computedOutcome,
            String engineVersion,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.meetingOccurrenceId = Objects.requireNonNull(meetingOccurrenceId, "meetingOccurrenceId");
        this.sourceExtractionId = Objects.requireNonNull(sourceExtractionId, "sourceExtractionId");
        this.ruleResults = List.copyOf(Objects.requireNonNull(ruleResults, "ruleResults"));
        this.computedOutcome = Objects.requireNonNull(computedOutcome, "computedOutcome");
        this.engineVersion = Objects.requireNonNull(engineVersion, "engineVersion");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static ValidationRun create(
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID sourceExtractionId,
            List<ValidationRuleResult> ruleResults,
            QualityGateOutcome computedOutcome,
            String engineVersion,
            Instant now
    ) {
        return new ValidationRun(
                UUID.randomUUID(),
                tenantId,
                meetingOccurrenceId,
                sourceExtractionId,
                ruleResults,
                computedOutcome,
                engineVersion,
                now
        );
    }

    public static ValidationRun rehydrate(
            UUID id,
            UUID tenantId,
            UUID meetingOccurrenceId,
            UUID sourceExtractionId,
            List<ValidationRuleResult> ruleResults,
            QualityGateOutcome computedOutcome,
            String engineVersion,
            Instant createdAt
    ) {
        return new ValidationRun(
                id,
                tenantId,
                meetingOccurrenceId,
                sourceExtractionId,
                ruleResults,
                computedOutcome,
                engineVersion,
                createdAt
        );
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UUID meetingOccurrenceId() {
        return meetingOccurrenceId;
    }

    public UUID sourceExtractionId() {
        return sourceExtractionId;
    }

    public List<ValidationRuleResult> ruleResults() {
        return ruleResults;
    }

    public QualityGateOutcome computedOutcome() {
        return computedOutcome;
    }

    public String engineVersion() {
        return engineVersion;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public long failCount() {
        return ruleResults.stream().filter(ValidationRuleResult::isFail).count();
    }

    public long warnCount() {
        return ruleResults.stream().filter(ValidationRuleResult::isWarn).count();
    }
}
