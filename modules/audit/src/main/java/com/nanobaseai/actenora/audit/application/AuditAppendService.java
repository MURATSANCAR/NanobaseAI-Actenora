package com.nanobaseai.actenora.audit.application;

import com.nanobaseai.actenora.audit.application.port.AuditEntryStore;
import com.nanobaseai.actenora.audit.domain.AuditContentGuard;
import com.nanobaseai.actenora.audit.domain.AuditEntry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class AuditAppendService {

    private final AuditEntryStore store;

    public AuditAppendService(AuditEntryStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public AuditEntry append(
            UUID tenantId,
            String actorId,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
        Map<String, Object> safeMetadata = AuditContentGuard.sanitize(metadata);
        return store.append(AuditEntry.append(
                tenantId, actorId, action, resourceType, resourceId, safeMetadata, occurredAt
        ));
    }

    public List<AuditEntry> timeline(UUID tenantId, UUID resourceId) {
        return store.timeline(tenantId, resourceId);
    }

    public List<AuditEntry> listForTenant(UUID tenantId) {
        return store.listByTenant(tenantId);
    }
}
