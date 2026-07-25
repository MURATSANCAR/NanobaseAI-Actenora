package com.nanobaseai.actenora.meeting.application.collaboration.port;

import com.nanobaseai.actenora.meeting.domain.collaboration.MeetingAgenda;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface MeetingAgendaRepository {

    MeetingAgenda save(MeetingAgenda agenda);

    Optional<MeetingAgenda> findByMeetingOccurrenceIdAndTenantId(UUID meetingOccurrenceId, TenantId tenantId);

    Optional<MeetingAgenda> findByIdAndTenantId(UUID id, TenantId tenantId);
}
