package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.meetingintelligence.domain.model.Commitment;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommitmentRepository {

    Commitment save(Commitment commitment);

    Optional<Commitment> findByIdAndTenantId(UUID id, TenantId tenantId);

    List<Commitment> findByNoteId(UUID noteId, TenantId tenantId);
}
