package com.nanobaseai.actenora.approval.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.util.UUID;

public final class ApprovalNotFoundException extends ActenoraException {

    public ApprovalNotFoundException(UUID approvalRequestId) {
        super("APPROVAL_NOT_FOUND", "Approval request not found: " + approvalRequestId);
    }
}
