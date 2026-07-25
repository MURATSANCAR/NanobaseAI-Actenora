package com.nanobaseai.actenora.meetingintelligence.infrastructure;

import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingIntelligenceAuditPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RecordingMeetingIntelligenceAuditPort implements MeetingIntelligenceAuditPort {

    public record Entry(
            UUID tenantId,
            String actorId,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
    }

    private final List<Entry> entries = Collections.synchronizedList(new ArrayList<>());

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
        entries.add(new Entry(tenantId, actorId, action, resourceType, resourceId, Map.copyOf(metadata), occurredAt));
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public List<Entry> timelineFor(UUID resourceId) {
        return entries.stream().filter(e -> e.resourceId().equals(resourceId)).toList();
    }
}
