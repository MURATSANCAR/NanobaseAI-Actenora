package com.nanobaseai.actenora.meetingintelligence.infrastructure.validation;

import com.nanobaseai.actenora.meetingintelligence.application.validation.port.ValidationAuditPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recording audit adapter for tests and local wiring.
 */
public final class RecordingValidationAuditPort implements ValidationAuditPort {

    public record Entry(
            UUID tenantId,
            String actor,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
    }

    private final List<Entry> entries = new ArrayList<>();

    @Override
    public synchronized void record(
            UUID tenantId,
            String actor,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
        entries.add(new Entry(tenantId, actor, action, resourceType, resourceId, Map.copyOf(metadata), occurredAt));
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    public synchronized boolean hasAction(String action) {
        return entries.stream().anyMatch(e -> e.action().equals(action));
    }
}
