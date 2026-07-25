package com.nanobaseai.actenora.template.infrastructure;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.template.api.MeetingTemplateId;
import com.nanobaseai.actenora.template.api.RenderJobId;
import com.nanobaseai.actenora.template.api.RenderedDocumentId;
import com.nanobaseai.actenora.template.api.TemplateApi;
import com.nanobaseai.actenora.template.api.TemplateVersionId;
import com.nanobaseai.actenora.template.application.DocumentRenderService;
import com.nanobaseai.actenora.template.application.TemplateStudioService;
import com.nanobaseai.actenora.template.application.port.out.RenderedDocumentRepository;
import com.nanobaseai.actenora.template.domain.RenderFormat;
import com.nanobaseai.actenora.template.domain.RenderJob;

import java.util.Optional;
import java.util.UUID;

public class TemplateApiFacade implements TemplateApi {

    private final TemplateStudioService studioService;
    private final DocumentRenderService renderService;
    private final RenderedDocumentRepository documentRepository;

    public TemplateApiFacade(
            TemplateStudioService studioService,
            DocumentRenderService renderService,
            RenderedDocumentRepository documentRepository) {
        this.studioService = studioService;
        this.renderService = renderService;
        this.documentRepository = documentRepository;
    }

    @Override
    public MeetingTemplateId createTemplate(TenantId tenantId, String name) {
        return studioService.createTemplate(tenantId, name).id();
    }

    @Override
    public TemplateVersionId createDraftVersion(TenantId tenantId, MeetingTemplateId templateId, String changelog) {
        return studioService.createDraftVersion(tenantId, templateId, changelog).id();
    }

    @Override
    public void saveDraftDesign(
            TenantId tenantId,
            TemplateVersionId versionId,
            String designSchemaJson,
            String contentSchemaJson) {
        studioService.saveDraftDesign(tenantId, versionId, designSchemaJson, contentSchemaJson);
    }

    @Override
    public TemplateVersionId publish(TenantId tenantId, TemplateVersionId versionId) {
        return studioService.publish(tenantId, versionId).id();
    }

    @Override
    public void lockNoteToTemplateVersion(TenantId tenantId, UUID noteId, TemplateVersionId versionId) {
        studioService.lockNote(tenantId, noteId, versionId);
    }

    @Override
    public Optional<TemplateVersionId> findLockedTemplateVersion(TenantId tenantId, UUID noteId) {
        return studioService.findLock(tenantId, noteId).map(lock -> lock.templateVersionId());
    }

    @Override
    public RenderJobId enqueueRender(
            TenantId tenantId,
            UUID noteId,
            TemplateVersionId versionId,
            String contentJson,
            String format) {
        RenderJob job = renderService.enqueue(
                tenantId, noteId, versionId, contentJson, RenderFormat.fromWire(format));
        return job.id();
    }

    @Override
    public Optional<RenderedDocumentId> findRenderedDocument(TenantId tenantId, RenderJobId jobId) {
        return documentRepository.findByJobId(tenantId, jobId).map(doc -> doc.id());
    }
}
