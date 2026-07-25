package com.nanobaseai.actenora.meeting.domain.exception;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

public final class TenantIsolationViolationException extends ActenoraException {

    public TenantIsolationViolationException() {
        super("TENANT_ISOLATION_VIOLATION", "Resource does not belong to the current tenant");
    }
}
