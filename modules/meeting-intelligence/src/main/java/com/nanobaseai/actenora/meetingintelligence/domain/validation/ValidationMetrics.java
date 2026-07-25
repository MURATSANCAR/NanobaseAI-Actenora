package com.nanobaseai.actenora.meetingintelligence.domain.validation;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregated validation metrics across runs (and optionally within a single run).
 */
public final class ValidationMetrics {

    private final long totalRuns;
    private final Map<QualityGateOutcome, Long> outcomes;
    private final Map<String, Long> failuresByRule;
    private final long totalFails;
    private final long totalWarns;

    public ValidationMetrics(
            long totalRuns,
            Map<QualityGateOutcome, Long> outcomes,
            Map<String, Long> failuresByRule,
            long totalFails,
            long totalWarns
    ) {
        this.totalRuns = totalRuns;
        this.outcomes = Map.copyOf(outcomes);
        this.failuresByRule = Map.copyOf(failuresByRule);
        this.totalFails = totalFails;
        this.totalWarns = totalWarns;
    }

    public static ValidationMetrics empty() {
        return new ValidationMetrics(0, Map.of(), Map.of(), 0, 0);
    }

    public static ValidationMetrics fromRuns(List<ValidationRun> runs) {
        Objects.requireNonNull(runs, "runs");
        Map<QualityGateOutcome, Long> outcomes = new EnumMap<>(QualityGateOutcome.class);
        Map<String, Long> failuresByRule = new LinkedHashMap<>();
        long fails = 0;
        long warns = 0;
        for (ValidationRun run : runs) {
            outcomes.merge(run.computedOutcome(), 1L, Long::sum);
            for (ValidationRuleResult result : run.ruleResults()) {
                if (result.isFail()) {
                    fails++;
                    failuresByRule.merge(result.ruleId(), 1L, Long::sum);
                } else if (result.isWarn()) {
                    warns++;
                }
            }
        }
        return new ValidationMetrics(runs.size(), outcomes, failuresByRule, fails, warns);
    }

    public static ValidationMetrics fromSingleRun(ValidationRun run) {
        return fromRuns(List.of(run));
    }

    public long totalRuns() {
        return totalRuns;
    }

    public Map<QualityGateOutcome, Long> outcomes() {
        return outcomes;
    }

    public Map<String, Long> failuresByRule() {
        return failuresByRule;
    }

    public long totalFails() {
        return totalFails;
    }

    public long totalWarns() {
        return totalWarns;
    }

    public long count(QualityGateOutcome outcome) {
        return outcomes.getOrDefault(outcome, 0L);
    }
}
