package com.nanobaseai.actenora.meeting.application.relation.port;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Emits immutable audit entries for relation mutations.
 * Implementations must not persist transcript/raw prompt content.
 */
public interface RelationAuditPort {

    void record(
            UUID tenantId,
            String actor,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            Instant occurredAt
    );
}
