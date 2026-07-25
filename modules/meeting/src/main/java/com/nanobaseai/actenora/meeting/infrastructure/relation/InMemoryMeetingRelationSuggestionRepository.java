package com.nanobaseai.actenora.meeting.infrastructure.relation;

import com.nanobaseai.actenora.meeting.application.relation.port.MeetingRelationSuggestionRepository;
import com.nanobaseai.actenora.meeting.domain.relation.MeetingRelationSuggestion;
import com.nanobaseai.actenora.meeting.domain.relation.SuggestionStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMeetingRelationSuggestionRepository implements MeetingRelationSuggestionRepository {

    private final Map<UUID, MeetingRelationSuggestion> byId = new ConcurrentHashMap<>();

    @Override
    public MeetingRelationSuggestion save(MeetingRelationSuggestion suggestion) {
        byId.put(suggestion.id(), suggestion);
        return suggestion;
    }

    @Override
    public Optional<MeetingRelationSuggestion> findById(UUID tenantId, UUID suggestionId) {
        return Optional.ofNullable(byId.get(suggestionId))
                .filter(s -> s.tenantId().equals(tenantId));
    }

    @Override
    public List<MeetingRelationSuggestion> findPendingByTenant(UUID tenantId) {
        return byId.values().stream()
                .filter(s -> s.tenantId().equals(tenantId))
                .filter(s -> s.status() == SuggestionStatus.PENDING)
                .toList();
    }
}
