package com.nanobaseai.actenora.template.api;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Public façade for Template Studio and document rendering.
 * Cross-module callers use types in this package only.
 */
public interface TemplateApi {

    MeetingTemplateId createTemplate(TenantId tenantId, String name);

    TemplateVersionId createDraftVersion(TenantId tenantId, MeetingTemplateId templateId, String changelog);

    void saveDraftDesign(TenantId tenantId, TemplateVersionId versionId, String designSchemaJson, String contentSchemaJson);

    TemplateVersionId publish(TenantId tenantId, TemplateVersionId versionId);

    void lockNoteToTemplateVersion(TenantId tenantId, UUID noteId, TemplateVersionId versionId);

    Optional<TemplateVersionId> findLockedTemplateVersion(TenantId tenantId, UUID noteId);

    RenderJobId enqueueRender(TenantId tenantId, UUID noteId, TemplateVersionId versionId, String contentJson, String format);

    Optional<RenderedDocumentId> findRenderedDocument(TenantId tenantId, RenderJobId jobId);
}
