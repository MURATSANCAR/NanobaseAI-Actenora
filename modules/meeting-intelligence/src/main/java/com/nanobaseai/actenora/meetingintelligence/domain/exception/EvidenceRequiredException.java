package com.nanobaseai.actenora.meetingintelligence.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

public final class EvidenceRequiredException extends ActenoraException {

    public EvidenceRequiredException(String subject) {
        super(
                "EVIDENCE_REQUIRED",
                "Evidence is required for " + subject + "; record marked for manual review"
        );
    }
}
