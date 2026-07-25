package com.nanobaseai.actenora.meeting.infrastructure.collaboration;

import com.nanobaseai.actenora.meeting.application.collaboration.port.OpenTaskRepository;
import com.nanobaseai.actenora.meeting.domain.collaboration.OpenTask;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryOpenTaskRepository implements OpenTaskRepository {

    private final Map<UUID, OpenTask> store = new ConcurrentHashMap<>();

    @Override
    public OpenTask save(OpenTask task) {
        store.put(task.id(), task);
        return task;
    }

    @Override
    public List<OpenTask> findOpenByMeetingOccurrenceIdAndTenantId(UUID meetingOccurrenceId, TenantId tenantId) {
        return store.values().stream()
                .filter(t -> t.meetingOccurrenceId().equals(meetingOccurrenceId))
                .filter(t -> t.tenantId().equals(tenantId))
                .filter(OpenTask::open)
                .toList();
    }

    public void clear() {
        store.clear();
    }
}
