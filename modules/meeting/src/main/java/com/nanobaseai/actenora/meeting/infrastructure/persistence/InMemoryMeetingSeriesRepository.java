package com.nanobaseai.actenora.meeting.infrastructure.persistence;

import com.nanobaseai.actenora.meeting.application.port.MeetingSeriesRepository;
import com.nanobaseai.actenora.meeting.domain.model.MeetingSeries;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMeetingSeriesRepository implements MeetingSeriesRepository {

    private final Map<UUID, MeetingSeries> store = new ConcurrentHashMap<>();

    @Override
    public MeetingSeries save(MeetingSeries series) {
        store.put(series.id(), series);
        return series;
    }

    @Override
    public Optional<MeetingSeries> findByIdAndTenantId(UUID id, TenantId tenantId) {
        return Optional.ofNullable(store.get(id))
                .filter(s -> s.tenantId().equals(tenantId));
    }

    public void clear() {
        store.clear();
    }
}
