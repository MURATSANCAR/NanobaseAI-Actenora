package com.nanobaseai.actenora.meeting.application.port;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Map;
import java.util.UUID;

/**
 * Emits audit records for meeting mutations. Audit BC owns persistence (FAZ 5).
 */
public interface MeetingAuditPort {

    void record(
            TenantId tenantId,
            UUID actorUserId,
            String action,
            String resourceType,
            UUID resourceId,
            Map<String, Object> metadata
    );
}
