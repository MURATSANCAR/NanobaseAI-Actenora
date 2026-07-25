package com.nanobaseai.actenora.delivery.application.port;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Local audit port — wired to Audit BC at composition root; never stores raw mail bodies.
 */
public interface DeliveryAuditPort {

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
