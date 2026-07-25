package com.nanobaseai.actenora.meetingintelligence.application.validation.port;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable audit trail for quality-gate overrides and manual review resolutions.
 * Must not persist transcript content or raw prompts.
 */
public interface ValidationAuditPort {

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
