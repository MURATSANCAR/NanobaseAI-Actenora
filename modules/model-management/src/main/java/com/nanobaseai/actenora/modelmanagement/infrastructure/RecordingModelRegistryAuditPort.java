package com.nanobaseai.actenora.modelmanagement.infrastructure;

import com.nanobaseai.actenora.modelmanagement.application.ModelRegistryAuditPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory audit sink for tests and local bootstrapping before Audit BC wiring.
 */
public final class RecordingModelRegistryAuditPort implements ModelRegistryAuditPort {

    public record Entry(
            String actorUserId,
            String action,
            String resourceType,
            String resourceId,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
    }

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void append(
            String actorUserId,
            String action,
            String resourceType,
            String resourceId,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
        entries.add(new Entry(
                actorUserId,
                action,
                resourceType,
                resourceId,
                metadata == null ? Map.of() : Map.copyOf(metadata),
                occurredAt
        ));
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public void clear() {
        entries.clear();
    }
}
