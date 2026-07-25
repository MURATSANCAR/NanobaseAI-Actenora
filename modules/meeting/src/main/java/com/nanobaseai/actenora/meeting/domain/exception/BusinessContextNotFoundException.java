package com.nanobaseai.actenora.meeting.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.util.UUID;

public final class BusinessContextNotFoundException extends ActenoraException {

    public BusinessContextNotFoundException(UUID id) {
        super("BUSINESS_CONTEXT_NOT_FOUND", "Business context not found: " + id);
    }
}
