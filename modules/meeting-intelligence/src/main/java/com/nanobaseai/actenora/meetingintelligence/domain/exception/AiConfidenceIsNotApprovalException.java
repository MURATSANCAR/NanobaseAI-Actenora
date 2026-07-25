package com.nanobaseai.actenora.meetingintelligence.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

public final class AiConfidenceIsNotApprovalException extends ActenoraException {

    public AiConfidenceIsNotApprovalException() {
        super(
                "AI_CONFIDENCE_IS_NOT_APPROVAL",
                "AI confidence must not be treated as human approval"
        );
    }
}
