package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.meetingintelligence.domain.model.Decision;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DecisionRepository {

    Decision save(Decision decision);

    Optional<Decision> findByIdAndTenantId(UUID id, TenantId tenantId);

    List<Decision> findByNoteId(UUID noteId, TenantId tenantId);
}
