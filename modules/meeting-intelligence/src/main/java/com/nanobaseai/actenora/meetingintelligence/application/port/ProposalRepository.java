package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.meetingintelligence.domain.model.Proposal;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProposalRepository {

    Proposal save(Proposal proposal);

    Optional<Proposal> findByIdAndTenantId(UUID id, TenantId tenantId);

    List<Proposal> findByNoteId(UUID noteId, TenantId tenantId);
}
