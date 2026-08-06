package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.meetingintelligence.domain.model.Topic;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TopicRepository {

    Topic save(Topic topic);

    Optional<Topic> findByIdAndTenantId(UUID id, TenantId tenantId);

    List<Topic> findByNoteId(UUID noteId, TenantId tenantId);
}
