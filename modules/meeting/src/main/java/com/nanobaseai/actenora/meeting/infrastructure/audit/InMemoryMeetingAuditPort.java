package com.nanobaseai.actenora.meeting.infrastructure.audit;

import com.nanobaseai.actenora.meeting.application.port.MeetingAuditPort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InMemoryMeetingAuditPort implements MeetingAuditPort {

    public record AuditEntry(
            TenantId tenantId,
            UUID actorUserId,
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
            TenantId tenantId,
            UUID actorUserId,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata
    ) {
        entries.add(new AuditEntry(
                tenantId,
                actorUserId,
                action,
                resourceType,
                resourceId,
                metadata == null ? Map.of() : Map.copyOf(metadata),
                Instant.now()
        ));
    }

    public List<AuditEntry> entries() {
        return List.copyOf(entries);
    }

    public void clear() {
        entries.clear();
    }
}
