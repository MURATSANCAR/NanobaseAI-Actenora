package com.nanobaseai.actenora.audit.infrastructure;

import com.nanobaseai.actenora.audit.api.AuditApi;
import com.nanobaseai.actenora.audit.application.AuditAppendService;
import com.nanobaseai.actenora.audit.domain.AuditEntry;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class AuditApiAdapter implements AuditApi {

    private final AuditAppendService service;

    public AuditApiAdapter(AuditAppendService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public UUID append(
            UUID tenantId,
            String actorId,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
        return service.append(tenantId, actorId, action, resourceType, resourceId, metadata, occurredAt).id();
    }

    @Override
    public List<AuditTimelineEntry> timeline(UUID tenantId, UUID resourceId) {
        return service.timeline(tenantId, resourceId).stream()
                .map(AuditApiAdapter::toEntry)
                .toList();
    }

    @Override
    public List<AuditTimelineEntry> listForTenant(UUID tenantId) {
        return service.listForTenant(tenantId).stream()
                .map(AuditApiAdapter::toEntry)
                .toList();
    }

    private static AuditTimelineEntry toEntry(AuditEntry e) {
        return new AuditTimelineEntry(
                e.id(), e.actorId(), e.action(), e.resourceType(), e.resourceId(), e.metadata(), e.occurredAt()
        );
    }
}
