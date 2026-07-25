package com.nanobaseai.actenora.audit.domain;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

/** Thrown when audit metadata would store forbidden sensitive content. */
public final class ForbiddenAuditContentException extends ActenoraException {

    private final String field;

    public ForbiddenAuditContentException(String field) {
        super("FORBIDDEN_AUDIT_CONTENT", "audit must not store field: " + field);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
