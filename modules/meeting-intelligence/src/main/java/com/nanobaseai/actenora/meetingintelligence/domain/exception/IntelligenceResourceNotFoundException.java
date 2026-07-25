package com.nanobaseai.actenora.meetingintelligence.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.util.UUID;

public final class IntelligenceResourceNotFoundException extends ActenoraException {

    public IntelligenceResourceNotFoundException(String type, UUID id) {
        super("INTELLIGENCE_RESOURCE_NOT_FOUND", type + " not found: " + id);
    }
}
