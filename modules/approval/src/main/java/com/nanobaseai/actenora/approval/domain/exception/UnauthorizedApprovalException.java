package com.nanobaseai.actenora.approval.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.util.UUID;

public final class UnauthorizedApprovalException extends ActenoraException {

    public UnauthorizedApprovalException(UUID approvalRequestId, String actorId) {
        super(
                "UNAUTHORIZED_APPROVAL",
                "Actor " + actorId + " is not authorized to decide approval " + approvalRequestId
        );
    }
}
