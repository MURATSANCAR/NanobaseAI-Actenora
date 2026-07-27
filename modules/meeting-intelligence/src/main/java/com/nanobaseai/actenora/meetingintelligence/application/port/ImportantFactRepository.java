package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.meetingintelligence.domain.model.ImportantFact;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportantFactRepository {

    ImportantFact save(ImportantFact importantFact);

    Optional<ImportantFact> findByIdAndTenantId(UUID id, TenantId tenantId);

    List<ImportantFact> findByNoteId(UUID noteId, TenantId tenantId);
}
