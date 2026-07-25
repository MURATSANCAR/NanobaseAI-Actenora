package com.nanobaseai.actenora.approval.api;

/**
 * Lifecycle of an approval request. Terminal once GRANTED, DENIED, or EXPIRED.
 * CHANGES_REQUESTED is re-openable after a new draft is submitted.
 */
public enum ApprovalRequestStatus {
    PENDING,
    GRANTED,
    DENIED,
    EXPIRED,
    CHANGES_REQUESTED
}
