package com.nanobaseai.actenora.template.application.port.out;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.template.domain.NoteTemplateLock;

import java.util.Optional;
import java.util.UUID;

public interface NoteTemplateLockRepository {

    void save(NoteTemplateLock lock);

    Optional<NoteTemplateLock> find(TenantId tenantId, UUID noteId);
}
