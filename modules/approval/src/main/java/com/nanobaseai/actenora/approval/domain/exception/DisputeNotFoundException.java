package com.nanobaseai.actenora.approval.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.util.UUID;

public final class DisputeNotFoundException extends ActenoraException {

    public DisputeNotFoundException(UUID disputeId) {
        super("DISPUTE_NOT_FOUND", "Participant dispute not found: " + disputeId);
    }
}
