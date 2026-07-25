package com.nanobaseai.actenora.meetingintelligence.domain.validation;

import java.util.List;
import java.util.Objects;

/**
 * Derives {@link QualityGateOutcome} from rule results and tenant thresholds.
 */
public final class QualityGateEvaluator {

    public QualityGateOutcome evaluate(List<ValidationRuleResult> results, QualityGateThreshold threshold) {
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(threshold, "threshold");

        long failCount = results.stream().filter(ValidationRuleResult::isFail).count();
        long warnCount = results.stream().filter(ValidationRuleResult::isWarn).count();

        boolean hardReject = results.stream()
                .filter(ValidationRuleResult::isFail)
                .anyMatch(r -> threshold.isHardReject(r.ruleId()));

        boolean confidenceReject = results.stream()
                .filter(ValidationRuleResult::isFail)
                .anyMatch(r -> ValidationRuleCodes.CONFIDENCE_THRESHOLD.equals(r.ruleId())
                        && threshold.rejectBelowConfidence());

        if (hardReject || confidenceReject || failCount > threshold.maxFailuresBeforeReject()) {
            return QualityGateOutcome.REJECTED;
        }
        if (failCount > 0) {
            return QualityGateOutcome.MANUAL_REVIEW_REQUIRED;
        }
        if (warnCount > 0) {
            return QualityGateOutcome.PASSED_WITH_WARNINGS;
        }
        return QualityGateOutcome.PASSED;
    }
}
