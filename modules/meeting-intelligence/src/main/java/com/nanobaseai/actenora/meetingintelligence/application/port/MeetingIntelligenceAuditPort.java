package com.nanobaseai.actenora.meetingintelligence.application.port;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface MeetingIntelligenceAuditPort {

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
