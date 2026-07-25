package com.nanobaseai.actenora.meetingintelligence.domain.validation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-rule outcome stored with every validation run (append-only history).
 */
public final class ValidationRuleResult {

    private final UUID id;
    private final String ruleId;
    private final String ruleVersion;
    private final RuleVerdict verdict;
    private final String message;
    private final String candidateKey;
    private final String detail;

    private ValidationRuleResult(
            UUID id,
            String ruleId,
            String ruleVersion,
            RuleVerdict verdict,
            String message,
            String candidateKey,
            String detail
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.ruleId = Objects.requireNonNull(ruleId, "ruleId");
        this.ruleVersion = Objects.requireNonNull(ruleVersion, "ruleVersion");
        this.verdict = Objects.requireNonNull(verdict, "verdict");
        this.message = Objects.requireNonNull(message, "message");
        this.candidateKey = candidateKey;
        this.detail = detail;
        if (ruleId.isBlank() || ruleVersion.isBlank()) {
            throw new IllegalArgumentException("ruleId and ruleVersion must not be blank");
        }
    }

    public static ValidationRuleResult of(
            String ruleId,
            String ruleVersion,
            RuleVerdict verdict,
            String message,
            String candidateKey,
            String detail
    ) {
        return new ValidationRuleResult(
                UUID.randomUUID(),
                ruleId,
                ruleVersion,
                verdict,
                message,
                candidateKey,
                detail
        );
    }

    public static ValidationRuleResult rehydrate(
            UUID id,
            String ruleId,
            String ruleVersion,
            RuleVerdict verdict,
            String message,
            String candidateKey,
            String detail
    ) {
        return new ValidationRuleResult(id, ruleId, ruleVersion, verdict, message, candidateKey, detail);
    }

    public UUID id() {
        return id;
    }

    public String ruleId() {
        return ruleId;
    }

    public String ruleVersion() {
        return ruleVersion;
    }

    public RuleVerdict verdict() {
        return verdict;
    }

    public String message() {
        return message;
    }

    public Optional<String> candidateKey() {
        return Optional.ofNullable(candidateKey);
    }

    public Optional<String> detail() {
        return Optional.ofNullable(detail);
    }

    public boolean isFail() {
        return verdict == RuleVerdict.FAIL;
    }

    public boolean isWarn() {
        return verdict == RuleVerdict.WARN;
    }
}
