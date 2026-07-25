package com.nanobaseai.actenora.meeting.application.relation.port;

import com.nanobaseai.actenora.meeting.domain.continuity.OccurrenceIdentitySnapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OccurrenceContinuityPort {

    Optional<OccurrenceIdentitySnapshot> findById(UUID tenantId, UUID occurrenceId);

    List<OccurrenceIdentitySnapshot> findAllByTenant(UUID tenantId);
}
