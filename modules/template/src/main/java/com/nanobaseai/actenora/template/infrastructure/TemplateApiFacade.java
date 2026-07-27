package com.nanobaseai.actenora.template.infrastructure;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.template.api.MeetingTemplateId;
import com.nanobaseai.actenora.template.api.RenderJobId;
import com.nanobaseai.actenora.template.api.RenderJobView;
import com.nanobaseai.actenora.template.api.RenderedDocumentId;
import com.nanobaseai.actenora.template.api.RenderedDocumentView;
import com.nanobaseai.actenora.template.api.TemplateApi;
import com.nanobaseai.actenora.template.api.TemplateVersionId;
import com.nanobaseai.actenora.template.application.DocumentRenderService;
import com.nanobaseai.actenora.template.application.TemplateStudioService;
import com.nanobaseai.actenora.template.application.port.out.RenderJobRepository;
import com.nanobaseai.actenora.template.application.port.out.RenderedDocumentRepository;
import com.nanobaseai.actenora.template.domain.RenderFormat;
import com.nanobaseai.actenora.template.domain.MeetingTemplate;
import com.nanobaseai.actenora.template.domain.RenderJob;
import com.nanobaseai.actenora.template.domain.TemplateVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TemplateApiFacade implements TemplateApi {

    private final TemplateStudioService studioService;
    private final DocumentRenderService renderService;
    private final RenderedDocumentRepository documentRepository;
    private final RenderJobRepository jobRepository;

    public TemplateApiFacade(
            TemplateStudioService studioService,
            DocumentRenderService renderService,
            RenderedDocumentRepository documentRepository,
            RenderJobRepository jobRepository) {
        this.studioService = studioService;
        this.renderService = renderService;
        this.documentRepository = documentRepository;
        this.jobRepository = jobRepository;
    }

    @Override
    public List<MeetingTemplate> listTemplates(TenantId tenantId) {
        return studioService.listTemplates(tenantId);
    }

    @Override
    public MeetingTemplate getTemplate(TenantId tenantId, MeetingTemplateId templateId) {
        return studioService.getTemplate(tenantId, templateId);
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
    public MeetingTemplate setDefaultTemplate(TenantId tenantId, MeetingTemplateId templateId) {
        return studioService.setDefaultTemplate(tenantId, templateId);
    }

    @Override
    public Optional<MeetingTemplate> findDefaultTemplate(TenantId tenantId) {
        return studioService.findDefaultTemplate(tenantId);
    }

    @Override
    public Optional<TemplateVersion> resolveDefaultVersion(TenantId tenantId) {
        return studioService.resolveDefaultVersion(tenantId);
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

    @Override
    public List<RenderJobView> listRenderJobsForNote(TenantId tenantId, UUID noteId) {
        return jobRepository.findByNoteId(tenantId, noteId).stream()
                .map(job -> new RenderJobView(
                        job.id().value(),
                        job.noteId(),
                        job.format().name(),
                        job.status().name(),
                        job.renderedDocumentId().map(RenderedDocumentId::value),
                        job.createdAt(),
                        job.updatedAt(),
                        job.lastError()
                ))
                .toList();
    }

    @Override
    public List<RenderedDocumentView> listRenderedDocumentsForNote(TenantId tenantId, UUID noteId) {
        return documentRepository.findByNoteId(tenantId, noteId).stream()
                .map(doc -> new RenderedDocumentView(
                        doc.id().value(),
                        doc.noteId(),
                        doc.renderJobId().value(),
                        doc.format().name(),
                        doc.storageKey(),
                        doc.sizeBytes(),
                        doc.createdAt()
                ))
                .toList();
    }
}
