package com.nanobaseai.actenora.meeting.application.relation.port;

import com.nanobaseai.actenora.meeting.domain.relation.MeetingRelation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingRelationRepository {

    MeetingRelation save(MeetingRelation relation);

    Optional<MeetingRelation> findById(UUID tenantId, UUID relationId);

    List<MeetingRelation> findAllByTenant(UUID tenantId);

    List<MeetingRelation> findByOccurrence(UUID tenantId, UUID occurrenceId);
}
