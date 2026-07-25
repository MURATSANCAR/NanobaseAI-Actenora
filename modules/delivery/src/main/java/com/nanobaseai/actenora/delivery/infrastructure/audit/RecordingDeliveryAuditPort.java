package com.nanobaseai.actenora.delivery.infrastructure.audit;

import com.nanobaseai.actenora.delivery.application.port.DeliveryAuditPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RecordingDeliveryAuditPort implements DeliveryAuditPort {

    public record AuditEntry(
            UUID tenantId,
            String actorId,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
    }

    private final List<AuditEntry> entries = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void record(
            UUID tenantId,
            String actorId,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
        entries.add(new AuditEntry(
                tenantId,
                actorId,
                action,
                resourceType,
                resourceId,
                Map.copyOf(metadata),
                occurredAt
        ));
    }

    public List<AuditEntry> entries() {
        return List.copyOf(entries);
    }

    public List<AuditEntry> ofAction(String action) {
        return entries.stream().filter(e -> e.action().equals(action)).toList();
    }
}
