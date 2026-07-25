package com.nanobaseai.actenora.meeting.infrastructure.persistence;

import com.nanobaseai.actenora.meeting.application.port.MeetingOccurrenceRepository;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrence;
import com.nanobaseai.actenora.meeting.domain.model.MeetingOccurrenceStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMeetingOccurrenceRepository implements MeetingOccurrenceRepository {

    private final Map<UUID, MeetingOccurrence> store = new ConcurrentHashMap<>();

    @Override
    public MeetingOccurrence save(MeetingOccurrence occurrence) {
        store.put(occurrence.id(), occurrence);
        return occurrence;
    }

    @Override
    public Optional<MeetingOccurrence> findByIdAndTenantId(UUID id, TenantId tenantId) {
        return Optional.ofNullable(store.get(id))
                .filter(o -> o.tenantId().equals(tenantId));
    }

    @Override
    public boolean existsByTenantIdAndGraphEventImmutableId(TenantId tenantId, String graphEventImmutableId) {
        return store.values().stream().anyMatch(o ->
                o.tenantId().equals(tenantId)
                        && Objects.equals(o.graphEventImmutableId(), graphEventImmutableId));
    }

    @Override
    public boolean existsByTenantIdAndIcalUidAndOriginalStartAt(
            TenantId tenantId, String icalUid, Instant originalStartAt) {
        return store.values().stream().anyMatch(o ->
                o.tenantId().equals(tenantId)
                        && Objects.equals(o.icalUid(), icalUid)
                        && Objects.equals(o.originalStartAt(), originalStartAt));
    }

    @Override
    public PageResult<MeetingOccurrence> findByTenant(
            TenantId tenantId,
            MeetingOccurrenceStatus status,
            UUID businessContextId,
            String cursor,
            int limit
    ) {
        List<MeetingOccurrence> filtered = store.values().stream()
                .filter(o -> o.tenantId().equals(tenantId))
                .filter(o -> status == null || o.status() == status)
                .filter(o -> businessContextId == null || o.businessContextId().equals(businessContextId))
                .sorted(Comparator.comparing(MeetingOccurrence::createdAt)
                        .thenComparing(MeetingOccurrence::id))
                .toList();

        int start = 0;
        if (cursor != null && !cursor.isBlank()) {
            UUID cursorId = UUID.fromString(cursor);
            for (int i = 0; i < filtered.size(); i++) {
                if (filtered.get(i).id().equals(cursorId)) {
                    start = i + 1;
                    break;
                }
            }
        }

        int end = Math.min(start + limit, filtered.size());
        List<MeetingOccurrence> page = filtered.subList(start, end);
        String next = end < filtered.size() ? page.get(page.size() - 1).id().toString() : null;
        return new PageResult<>(page, next);
    }

    public void clear() {
        store.clear();
    }

    public Optional<MeetingOccurrence> findByIdAnyTenant(UUID id) {
        return Optional.ofNullable(store.get(id));
    }
}
