package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.meetingintelligence.domain.model.Risk;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskRepository {

    Risk save(Risk risk);

    Optional<Risk> findByIdAndTenantId(UUID id, TenantId tenantId);

    List<Risk> findByNoteId(UUID noteId, TenantId tenantId);
}
