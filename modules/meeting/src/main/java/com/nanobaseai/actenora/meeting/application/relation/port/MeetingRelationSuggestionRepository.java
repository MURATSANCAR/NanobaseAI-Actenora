package com.nanobaseai.actenora.meeting.application.relation.port;

import com.nanobaseai.actenora.meeting.domain.relation.MeetingRelationSuggestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingRelationSuggestionRepository {

    MeetingRelationSuggestion save(MeetingRelationSuggestion suggestion);

    Optional<MeetingRelationSuggestion> findById(UUID tenantId, UUID suggestionId);

    List<MeetingRelationSuggestion> findPendingByTenant(UUID tenantId);
}
