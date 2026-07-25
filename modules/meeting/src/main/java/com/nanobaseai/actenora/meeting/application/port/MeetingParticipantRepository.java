package com.nanobaseai.actenora.meeting.application.port;

import com.nanobaseai.actenora.meeting.domain.model.MeetingParticipant;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.UUID;

public interface MeetingParticipantRepository {

    MeetingParticipant save(MeetingParticipant participant);

    List<MeetingParticipant> findByMeetingOccurrenceIdAndTenantId(UUID meetingOccurrenceId, TenantId tenantId);
}
