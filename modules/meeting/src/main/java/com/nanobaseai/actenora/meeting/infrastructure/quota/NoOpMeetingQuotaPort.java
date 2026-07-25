package com.nanobaseai.actenora.meeting.infrastructure.quota;

import com.nanobaseai.actenora.meeting.application.port.MeetingQuotaPort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

/** No-op default when Policy BC is not wired (unit tests / isolated module). */
public final class NoOpMeetingQuotaPort implements MeetingQuotaPort {

    @Override
    public void assertCanCreateMeeting(TenantId tenantId) {
        // unrestricted
    }

    @Override
    public void recordMeetingCreated(TenantId tenantId) {
        // no-op
    }
}
