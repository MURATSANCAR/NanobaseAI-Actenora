package com.nanobaseai.actenora.meeting.infrastructure.relation;

import com.nanobaseai.actenora.meeting.application.relation.port.RelationAuditPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RecordingRelationAuditPort implements RelationAuditPort {

    public record AuditEntry(
            UUID tenantId,
            String actor,
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
            String actor,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
        entries.add(new AuditEntry(
                tenantId,
                actor,
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
}
