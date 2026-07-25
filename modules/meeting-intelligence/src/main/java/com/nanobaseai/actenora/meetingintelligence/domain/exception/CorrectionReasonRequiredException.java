package com.nanobaseai.actenora.meetingintelligence.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

public final class CorrectionReasonRequiredException extends ActenoraException {

    public CorrectionReasonRequiredException() {
        super(
                "CORRECTION_REASON_REQUIRED",
                "Human edits must include a correction reason"
        );
    }
}
