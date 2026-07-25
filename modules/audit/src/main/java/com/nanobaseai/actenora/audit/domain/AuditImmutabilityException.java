package com.nanobaseai.actenora.audit.domain;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

/** Thrown when an attempt is made to mutate an immutable audit entry. */
public final class AuditImmutabilityException extends ActenoraException {

    public AuditImmutabilityException(String message) {
        super("AUDIT_IMMUTABLE", message);
    }
}
