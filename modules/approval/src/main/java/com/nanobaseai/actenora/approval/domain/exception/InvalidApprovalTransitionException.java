package com.nanobaseai.actenora.approval.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import com.nanobaseai.actenora.approval.api.ApprovalRequestStatus;

public final class InvalidApprovalTransitionException extends ActenoraException {

    public InvalidApprovalTransitionException(ApprovalRequestStatus from, ApprovalRequestStatus to) {
        super(
                "INVALID_APPROVAL_TRANSITION",
                "Cannot transition approval from " + from + " to " + to
        );
    }

    public InvalidApprovalTransitionException(String message) {
        super("INVALID_APPROVAL_TRANSITION", message);
    }
}
