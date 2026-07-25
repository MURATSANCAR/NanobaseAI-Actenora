package com.nanobaseai.actenora.modelmanagement.application;

import java.time.Instant;
import java.util.Map;

/**
 * Audit sink for control-plane mutations (wired to Audit BC in FAZ 5).
 */
public interface ModelRegistryAuditPort {

    void append(
            String actorUserId,
            String action,
            String resourceType,
            String resourceId,
            Map<String, Object> metadata,
            Instant occurredAt
    );
}
