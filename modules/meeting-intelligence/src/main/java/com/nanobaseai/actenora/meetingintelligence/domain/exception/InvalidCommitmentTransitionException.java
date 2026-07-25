package com.nanobaseai.actenora.meetingintelligence.domain.exception;

import com.nanobaseai.actenora.meetingintelligence.domain.model.CommitmentConfirmationStatus;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

public final class InvalidCommitmentTransitionException extends ActenoraException {

    public InvalidCommitmentTransitionException(
            CommitmentConfirmationStatus from,
            CommitmentConfirmationStatus to
    ) {
        super(
                "INVALID_COMMITMENT_TRANSITION",
                "Cannot transition commitment from " + from + " to " + to
        );
    }
}
