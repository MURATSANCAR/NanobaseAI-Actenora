package com.nanobaseai.actenora.audit.application.port;

import com.nanobaseai.actenora.audit.domain.AuditEntry;

import java.util.List;
import java.util.UUID;

public interface AuditEntryStore {

    AuditEntry append(AuditEntry entry);

    List<AuditEntry> timeline(UUID tenantId, UUID resourceId);

    /** FAZ 27 — tenant-scoped listing for audit retention / archive eligibility. */
    List<AuditEntry> listByTenant(UUID tenantId);
}
