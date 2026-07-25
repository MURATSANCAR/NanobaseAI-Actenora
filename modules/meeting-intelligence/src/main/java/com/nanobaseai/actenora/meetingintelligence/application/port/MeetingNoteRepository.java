package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNote;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface MeetingNoteRepository {

    MeetingNote save(MeetingNote note);

    Optional<MeetingNote> findByIdAndTenantId(UUID id, TenantId tenantId);
}
