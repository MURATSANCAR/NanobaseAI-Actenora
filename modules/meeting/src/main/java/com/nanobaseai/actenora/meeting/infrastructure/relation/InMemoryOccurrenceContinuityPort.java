package com.nanobaseai.actenora.meeting.infrastructure.relation;

import com.nanobaseai.actenora.meeting.application.relation.port.OccurrenceContinuityPort;
import com.nanobaseai.actenora.meeting.domain.continuity.OccurrenceIdentitySnapshot;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryOccurrenceContinuityPort implements OccurrenceContinuityPort {

    private final Map<UUID, OccurrenceIdentitySnapshot> byId = new ConcurrentHashMap<>();

    public void put(OccurrenceIdentitySnapshot snapshot) {
        byId.put(snapshot.occurrenceId(), snapshot);
    }

    @Override
    public Optional<OccurrenceIdentitySnapshot> findById(UUID tenantId, UUID occurrenceId) {
        return Optional.ofNullable(byId.get(occurrenceId))
                .filter(o -> o.belongsToTenant(tenantId));
    }

    @Override
    public List<OccurrenceIdentitySnapshot> findAllByTenant(UUID tenantId) {
        return byId.values().stream()
                .filter(o -> o.belongsToTenant(tenantId))
                .toList();
    }
}
