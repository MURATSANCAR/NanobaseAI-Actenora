package com.nanobaseai.actenora.template.application;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import com.nanobaseai.actenora.template.api.RenderJobId;
import com.nanobaseai.actenora.template.api.TemplateVersionId;
import com.nanobaseai.actenora.template.application.port.out.MeetingTemplateRepository;
import com.nanobaseai.actenora.template.application.port.out.NoteTemplateLockRepository;
import com.nanobaseai.actenora.template.application.port.out.RenderJobRepository;
import com.nanobaseai.actenora.template.application.port.out.RenderedDocumentRepository;
import com.nanobaseai.actenora.template.domain.ContentHash;
import com.nanobaseai.actenora.template.domain.NoteTemplateLock;
import com.nanobaseai.actenora.template.domain.RenderFormat;
import com.nanobaseai.actenora.template.domain.RenderJob;
import com.nanobaseai.actenora.template.domain.RenderJobStatus;
import com.nanobaseai.actenora.template.domain.TemplateDomainException;
import com.nanobaseai.actenora.template.domain.TemplateVersion;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Enqueues render jobs with content-hash idempotency.
 */
public class DocumentRenderService {

    private final MeetingTemplateRepository templateRepository;
    private final NoteTemplateLockRepository lockRepository;
    private final RenderJobRepository jobRepository;
    private final RenderedDocumentRepository documentRepository;
    private final InstantClock clock;

    public DocumentRenderService(
            MeetingTemplateRepository templateRepository,
            NoteTemplateLockRepository lockRepository,
            RenderJobRepository jobRepository,
            RenderedDocumentRepository documentRepository,
            InstantClock clock) {
        this.templateRepository = Objects.requireNonNull(templateRepository);
        this.lockRepository = Objects.requireNonNull(lockRepository);
        this.jobRepository = Objects.requireNonNull(jobRepository);
        this.documentRepository = Objects.requireNonNull(documentRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public RenderJob enqueue(
            TenantId tenantId,
            UUID noteId,
            TemplateVersionId versionId,
            String contentJson,
            RenderFormat format) {
        TemplateVersion version = templateRepository.findVersion(tenantId, versionId)
                .orElseThrow(() -> new TemplateDomainException(
                        "VERSION_NOT_FOUND", "Template version not found: " + versionId.value()));
        version.assertPublished();

        Optional<NoteTemplateLock> lock = lockRepository.find(tenantId, noteId);
        if (lock.isPresent()) {
            lock.get().assertSameVersion(versionId);
        } else {
            // First render implicitly locks the note to this published version.
            lockRepository.save(new NoteTemplateLock(tenantId, noteId, versionId, clock.now()));
        }

        ContentHash hash = RenderJob.computeIdempotencyHash(versionId, contentJson, format);
        Optional<RenderJob> existing = jobRepository.findByContentHash(tenantId, hash);
        if (existing.isPresent()) {
            return existing.get();
        }

        Instant now = clock.now();
        RenderJob job = RenderJob.builder()
                .id(RenderJobId.random())
                .tenantId(tenantId)
                .noteId(noteId)
                .templateVersionId(versionId)
                .format(format)
                .contentHash(hash)
                .contentJson(contentJson)
                .status(RenderJobStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
        jobRepository.save(job);
        return job;
    }

    public Optional<RenderJob> findJob(TenantId tenantId, RenderJobId jobId) {
        return jobRepository.findById(tenantId, jobId);
    }
}
