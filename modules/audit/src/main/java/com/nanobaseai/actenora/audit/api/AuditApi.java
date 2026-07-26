package com.nanobaseai.actenora.audit.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public façade for the Audit bounded context.
 */
public interface AuditApi {

    UUID append(
            UUID tenantId,
            String actorId,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            Instant occurredAt
    );

    List<AuditTimelineEntry> timeline(UUID tenantId, UUID resourceId);

    List<AuditTimelineEntry> listForTenant(UUID tenantId);

    record AuditTimelineEntry(
            UUID id,
            String actorId,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
    }
}
