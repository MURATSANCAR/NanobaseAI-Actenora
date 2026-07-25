package com.nanobaseai.actenora.meeting.application.collaboration.port;

import com.nanobaseai.actenora.meeting.domain.collaboration.MeetingMarker;
import com.nanobaseai.actenora.meeting.domain.collaboration.MarkerType;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingMarkerRepository {

    MeetingMarker save(MeetingMarker marker);

    Optional<MeetingMarker> findByTenantAndIdempotencyKey(TenantId tenantId, UUID actorUserId, String idempotencyKey);

    List<MeetingMarker> findByMeetingOccurrenceIdAndTenantId(UUID meetingOccurrenceId, TenantId tenantId);

    List<MeetingMarker> findByMeetingOccurrenceIdAndTenantIdAndType(
            UUID meetingOccurrenceId,
            TenantId tenantId,
            MarkerType type
    );
}
