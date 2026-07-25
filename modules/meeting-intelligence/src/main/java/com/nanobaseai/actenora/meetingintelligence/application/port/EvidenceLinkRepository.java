package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.meetingintelligence.domain.model.EvidenceLink;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvidenceLinkRepository {

    EvidenceLink save(EvidenceLink link);

    Optional<EvidenceLink> findByIdAndTenantId(UUID id, TenantId tenantId);

    List<EvidenceLink> findByNoteId(UUID noteId, TenantId tenantId);

    List<EvidenceLink> findBySubjectId(UUID subjectId, TenantId tenantId);
}
