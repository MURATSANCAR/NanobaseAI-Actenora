package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.meetingintelligence.domain.model.OpenQuestion;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OpenQuestionRepository {

    OpenQuestion save(OpenQuestion openQuestion);

    Optional<OpenQuestion> findByIdAndTenantId(UUID id, TenantId tenantId);

    List<OpenQuestion> findByNoteId(UUID noteId, TenantId tenantId);
}
