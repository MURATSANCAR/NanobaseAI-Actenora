package com.nanobaseai.actenora.meetingintelligence.domain.validation;

/**
 * Deterministic quality-gate result before an AI candidate becomes an official note draft.
 */
public enum QualityGateOutcome {
    PASSED,
    PASSED_WITH_WARNINGS,
    MANUAL_REVIEW_REQUIRED,
    REJECTED
}
