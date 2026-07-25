package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.meetingintelligence.domain.model.MeetingNoteVersion;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingNoteVersionRepository {

    MeetingNoteVersion save(MeetingNoteVersion version);

    Optional<MeetingNoteVersion> findByIdAndTenantId(UUID id, TenantId tenantId);

    Optional<MeetingNoteVersion> findByNoteIdAndVersionNumber(UUID noteId, int versionNumber, TenantId tenantId);

    List<MeetingNoteVersion> findAllByNoteId(UUID noteId, TenantId tenantId);
}
