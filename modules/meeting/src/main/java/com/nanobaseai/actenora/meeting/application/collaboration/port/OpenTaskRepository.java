package com.nanobaseai.actenora.meeting.application.collaboration.port;

import com.nanobaseai.actenora.meeting.domain.collaboration.OpenTask;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.UUID;

public interface OpenTaskRepository {

    OpenTask save(OpenTask task);

    List<OpenTask> findOpenByMeetingOccurrenceIdAndTenantId(UUID meetingOccurrenceId, TenantId tenantId);
}
