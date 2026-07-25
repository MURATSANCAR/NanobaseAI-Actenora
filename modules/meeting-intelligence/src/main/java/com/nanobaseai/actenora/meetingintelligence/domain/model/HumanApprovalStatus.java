package com.nanobaseai.actenora.meetingintelligence.domain.model;

/**
 * Human approval must never be inferred from AI confidence.
 */
public enum HumanApprovalStatus {
    NONE,
    APPROVED,
    REJECTED
}
