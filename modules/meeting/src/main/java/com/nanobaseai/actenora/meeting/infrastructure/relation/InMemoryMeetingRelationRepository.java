package com.nanobaseai.actenora.meeting.infrastructure.relation;

import com.nanobaseai.actenora.meeting.application.relation.port.MeetingRelationRepository;
import com.nanobaseai.actenora.meeting.domain.relation.MeetingRelation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMeetingRelationRepository implements MeetingRelationRepository {

    private final Map<UUID, MeetingRelation> byId = new ConcurrentHashMap<>();

    @Override
    public MeetingRelation save(MeetingRelation relation) {
        byId.put(relation.id(), relation);
        return relation;
    }

    @Override
    public Optional<MeetingRelation> findById(UUID tenantId, UUID relationId) {
        return Optional.ofNullable(byId.get(relationId))
                .filter(r -> r.tenantId().equals(tenantId));
    }

    @Override
    public List<MeetingRelation> findAllByTenant(UUID tenantId) {
        return byId.values().stream()
                .filter(r -> r.tenantId().equals(tenantId))
                .toList();
    }

    @Override
    public List<MeetingRelation> findByOccurrence(UUID tenantId, UUID occurrenceId) {
        List<MeetingRelation> result = new ArrayList<>();
        for (MeetingRelation relation : byId.values()) {
            if (!relation.tenantId().equals(tenantId)) {
                continue;
            }
            if (relation.sourceOccurrenceId().equals(occurrenceId)
                    || relation.targetOccurrenceId().equals(occurrenceId)) {
                result.add(relation);
            }
        }
        return List.copyOf(result);
    }
}
