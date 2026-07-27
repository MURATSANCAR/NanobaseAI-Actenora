package com.nanobaseai.actenora.meeting.application.port;

import com.nanobaseai.actenora.meeting.domain.model.MeetingSeries;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface MeetingSeriesRepository {

    MeetingSeries save(MeetingSeries series);

    Optional<MeetingSeries> findByIdAndTenantId(UUID id, TenantId tenantId);

    Optional<MeetingSeries> findByTenantIdAndGraphSeriesMasterId(TenantId tenantId, String graphSeriesMasterId);
}
