package com.nanobaseai.actenora.meetingintelligence.domain.validation;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/**
 * Tenant-configurable thresholds for the AI quality gate.
 */
public final class QualityGateThreshold {

    private final BigDecimal minConfidence;
    private final boolean rejectBelowConfidence;
    private final int maxFailuresBeforeReject;
    private final Set<String> hardRejectRuleIds;

    public QualityGateThreshold(
            BigDecimal minConfidence,
            boolean rejectBelowConfidence,
            int maxFailuresBeforeReject,
            Set<String> hardRejectRuleIds
    ) {
        this.minConfidence = Objects.requireNonNull(minConfidence, "minConfidence");
        if (minConfidence.compareTo(BigDecimal.ZERO) < 0 || minConfidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("minConfidence must be between 0 and 1");
        }
        if (maxFailuresBeforeReject < 0) {
            throw new IllegalArgumentException("maxFailuresBeforeReject must be >= 0");
        }
        this.rejectBelowConfidence = rejectBelowConfidence;
        this.maxFailuresBeforeReject = maxFailuresBeforeReject;
        this.hardRejectRuleIds = Set.copyOf(Objects.requireNonNull(hardRejectRuleIds, "hardRejectRuleIds"));
    }

    public static QualityGateThreshold systemDefault() {
        return new QualityGateThreshold(
                new BigDecimal("0.60"),
                false,
                2,
                Set.of(
                        ValidationRuleCodes.EVIDENCE_SEGMENT_EXISTS,
                        ValidationRuleCodes.OFFSET_VALID
                )
        );
    }

    public BigDecimal minConfidence() {
        return minConfidence;
    }

    public boolean rejectBelowConfidence() {
        return rejectBelowConfidence;
    }

    public int maxFailuresBeforeReject() {
        return maxFailuresBeforeReject;
    }

    public Set<String> hardRejectRuleIds() {
        return hardRejectRuleIds;
    }

    public boolean isHardReject(String ruleId) {
        return hardRejectRuleIds.contains(ruleId);
    }
}
