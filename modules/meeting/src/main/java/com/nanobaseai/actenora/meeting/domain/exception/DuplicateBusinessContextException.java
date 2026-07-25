package com.nanobaseai.actenora.meeting.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

public final class DuplicateBusinessContextException extends ActenoraException {

    public DuplicateBusinessContextException(String referenceCode) {
        super(
                "DUPLICATE_BUSINESS_CONTEXT",
                "Business context already exists for reference code: " + referenceCode
        );
    }
}
