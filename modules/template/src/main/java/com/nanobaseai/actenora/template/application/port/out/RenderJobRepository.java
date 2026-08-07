package com.nanobaseai.actenora.template.application.port.out;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.template.api.RenderJobId;
import com.nanobaseai.actenora.template.domain.ContentHash;
import com.nanobaseai.actenora.template.domain.RenderJob;

import java.util.List;
import java.util.Optional;

public interface RenderJobRepository {

    void save(RenderJob job);

    Optional<RenderJob> findById(TenantId tenantId, RenderJobId id);

    Optional<RenderJob> findByContentHash(TenantId tenantId, ContentHash contentHash);

    List<RenderJob> findPending(int limit);

    List<RenderJob> findByNoteId(TenantId tenantId, java.util.UUID noteId);
}
