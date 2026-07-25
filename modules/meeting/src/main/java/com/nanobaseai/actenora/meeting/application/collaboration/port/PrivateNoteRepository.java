package com.nanobaseai.actenora.meeting.application.collaboration.port;

import com.nanobaseai.actenora.meeting.domain.collaboration.PrivateNote;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrivateNoteRepository {

    PrivateNote save(PrivateNote note);

    Optional<PrivateNote> findByIdAndTenantId(UUID id, TenantId tenantId);

    Optional<PrivateNote> findByMeetingOccurrenceIdAndOwnerAndTenantId(
            UUID meetingOccurrenceId,
            UUID ownerUserId,
            TenantId tenantId
    );

    List<PrivateNote> findByMeetingOccurrenceIdAndTenantId(UUID meetingOccurrenceId, TenantId tenantId);

    /** FAZ 27 retention deletion — hard-delete private note body. */
    void delete(UUID id, TenantId tenantId);

    List<PrivateNote> findAllByTenantId(TenantId tenantId);
}
