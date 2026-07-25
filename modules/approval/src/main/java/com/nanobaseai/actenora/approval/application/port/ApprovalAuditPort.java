package com.nanobaseai.actenora.approval.application.port;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Local audit port — wired to audit BC at composition root; never imports audit.domain.
 */
public interface ApprovalAuditPort {

    void record(
            UUID tenantId,
            String actorId,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata,
            Instant occurredAt
    );
}
