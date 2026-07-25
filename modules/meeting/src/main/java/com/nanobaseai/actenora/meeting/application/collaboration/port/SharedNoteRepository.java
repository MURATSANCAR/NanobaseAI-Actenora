package com.nanobaseai.actenora.meeting.application.collaboration.port;

import com.nanobaseai.actenora.meeting.domain.collaboration.SharedNote;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface SharedNoteRepository {

    SharedNote save(SharedNote note);

    Optional<SharedNote> findByMeetingOccurrenceIdAndTenantId(UUID meetingOccurrenceId, TenantId tenantId);

    Optional<SharedNote> findByIdAndTenantId(UUID id, TenantId tenantId);
}
