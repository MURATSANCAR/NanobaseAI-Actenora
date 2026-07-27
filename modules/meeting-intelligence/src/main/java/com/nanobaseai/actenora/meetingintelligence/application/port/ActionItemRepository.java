package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.meetingintelligence.domain.model.ActionItem;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActionItemRepository {

    ActionItem save(ActionItem actionItem);

    Optional<ActionItem> findByIdAndTenantId(UUID id, TenantId tenantId);

    List<ActionItem> findByNoteId(UUID noteId, TenantId tenantId);

    List<ActionItem> findByTenantId(TenantId tenantId);
}
