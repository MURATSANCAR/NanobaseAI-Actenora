package com.nanobaseai.actenora.meeting.application.port;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

/**
 * Quota admission for meeting creates. Platform wires PolicyApi-backed implementation (FAZ 5).
 */
public interface MeetingQuotaPort {

    void assertCanCreateMeeting(TenantId tenantId);

    void recordMeetingCreated(TenantId tenantId);
}
