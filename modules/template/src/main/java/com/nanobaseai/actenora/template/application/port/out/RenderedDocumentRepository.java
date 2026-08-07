package com.nanobaseai.actenora.template.application.port.out;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.template.api.RenderJobId;
import com.nanobaseai.actenora.template.api.RenderedDocumentId;
import com.nanobaseai.actenora.template.domain.RenderedDocument;

import java.util.Optional;

public interface RenderedDocumentRepository {

    void save(RenderedDocument document);

    Optional<RenderedDocument> findById(TenantId tenantId, RenderedDocumentId id);

    Optional<RenderedDocument> findByJobId(TenantId tenantId, RenderJobId jobId);

    java.util.List<RenderedDocument> findByNoteId(TenantId tenantId, java.util.UUID noteId);
}
