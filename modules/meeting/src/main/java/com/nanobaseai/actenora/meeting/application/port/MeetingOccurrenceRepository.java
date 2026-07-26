package com.nanobaseai.actenora.meeting.application.port;

import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrence;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingOccurrenceRepository {

    MeetingOccurrence save(MeetingOccurrence occurrence);

    Optional<MeetingOccurrence> findByIdAndTenantId(UUID id, TenantId tenantId);

    Optional<MeetingOccurrence> findByTenantIdAndGraphEventImmutableId(
            TenantId tenantId, String graphEventImmutableId);

    boolean existsByTenantIdAndGraphEventImmutableId(TenantId tenantId, String graphEventImmutableId);

    boolean existsByTenantIdAndIcalUidAndOriginalStartAt(TenantId tenantId, String icalUid, Instant originalStartAt);

    PageResult<MeetingOccurrence> findByTenant(
            TenantId tenantId,
            MeetingOccurrenceStatus status,
            UUID businessContextId,
            String cursor,
            int limit
    );

    record PageResult<T>(List<T> items, String nextCursor) {
    }
}
