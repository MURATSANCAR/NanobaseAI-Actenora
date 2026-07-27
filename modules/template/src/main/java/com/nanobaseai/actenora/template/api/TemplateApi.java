package com.nanobaseai.actenora.template.api;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.template.domain.MeetingTemplate;
import com.nanobaseai.actenora.template.domain.TemplateVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public façade for Template Studio and document rendering.
 * Cross-module callers use types in this package only.
 */
public interface TemplateApi {

    List<MeetingTemplate> listTemplates(TenantId tenantId);

    MeetingTemplate getTemplate(TenantId tenantId, MeetingTemplateId templateId);

    MeetingTemplateId createTemplate(TenantId tenantId, String name);

    TemplateVersionId createDraftVersion(TenantId tenantId, MeetingTemplateId templateId, String changelog);

    void saveDraftDesign(TenantId tenantId, TemplateVersionId versionId, String designSchemaJson, String contentSchemaJson);

    TemplateVersionId publish(TenantId tenantId, TemplateVersionId versionId);

    /** Promotes the template to tenant default; new notes bind to its latest published version. */
    MeetingTemplate setDefaultTemplate(TenantId tenantId, MeetingTemplateId templateId);

    Optional<MeetingTemplate> findDefaultTemplate(TenantId tenantId);

    /** Latest published version of the tenant default template, if one is configured. */
    Optional<TemplateVersion> resolveDefaultVersion(TenantId tenantId);

    void lockNoteToTemplateVersion(TenantId tenantId, UUID noteId, TemplateVersionId versionId);

    Optional<TemplateVersionId> findLockedTemplateVersion(TenantId tenantId, UUID noteId);

    RenderJobId enqueueRender(TenantId tenantId, UUID noteId, TemplateVersionId versionId, String contentJson, String format);

    Optional<RenderedDocumentId> findRenderedDocument(TenantId tenantId, RenderJobId jobId);

    java.util.List<RenderJobView> listRenderJobsForNote(TenantId tenantId, UUID noteId);

    java.util.List<RenderedDocumentView> listRenderedDocumentsForNote(TenantId tenantId, UUID noteId);
}
