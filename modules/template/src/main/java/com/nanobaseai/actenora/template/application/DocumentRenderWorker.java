package com.nanobaseai.actenora.template.application;

import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectPutRequest;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorage;
import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorageException;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import com.nanobaseai.actenora.template.api.RenderedDocumentId;
import com.nanobaseai.actenora.template.application.port.out.DocumentRenderer;
import com.nanobaseai.actenora.template.application.port.out.MeetingTemplateRepository;
import com.nanobaseai.actenora.template.application.port.out.RenderJobRepository;
import com.nanobaseai.actenora.template.application.port.out.RenderedDocumentRepository;
import com.nanobaseai.actenora.template.domain.RenderJob;
import com.nanobaseai.actenora.template.domain.RenderJobStatus;
import com.nanobaseai.actenora.template.domain.RenderedDocument;
import com.nanobaseai.actenora.template.domain.TemplateDomainException;
import com.nanobaseai.actenora.template.domain.TemplateObjectKeys;
import com.nanobaseai.actenora.template.domain.TemplateVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Processes pending render jobs. Designed to run inside the monolith worker profile
 * or be extracted later into services/document-renderer.
 */
public class DocumentRenderWorker {

    private static final Logger log = LoggerFactory.getLogger(DocumentRenderWorker.class);

    private final RenderJobRepository jobRepository;
    private final RenderedDocumentRepository documentRepository;
    private final MeetingTemplateRepository templateRepository;
    private final DocumentRenderer renderer;
    private final ObjectStorage objectStorage;
    private final InstantClock clock;

    public DocumentRenderWorker(
            RenderJobRepository jobRepository,
            RenderedDocumentRepository documentRepository,
            MeetingTemplateRepository templateRepository,
            DocumentRenderer renderer,
            ObjectStorage objectStorage,
            InstantClock clock) {
        this.jobRepository = Objects.requireNonNull(jobRepository);
        this.documentRepository = Objects.requireNonNull(documentRepository);
        this.templateRepository = Objects.requireNonNull(templateRepository);
        this.renderer = Objects.requireNonNull(renderer);
        this.objectStorage = Objects.requireNonNull(objectStorage);
        this.clock = Objects.requireNonNull(clock);
    }

    public int processPending(int batchSize) {
        List<RenderJob> pending = jobRepository.findPending(batchSize);
        int processed = 0;
        for (RenderJob job : pending) {
            try {
                processOne(job);
            } catch (RuntimeException ex) {
                // Failure already persisted; continue batch so worker can retry later.
                log.debug("Render job {} failed in batch: {}", job.id().value(), ex.getMessage());
            }
            processed++;
        }
        return processed;
    }

    public void processOne(RenderJob job) {
        Instant now = clock.now();
        if (job.status() == RenderJobStatus.COMPLETED) {
            return;
        }
        job.markRunning(now);
        jobRepository.save(job);

        try {
            TemplateVersion version = templateRepository
                    .findVersion(job.tenantId(), job.templateVersionId())
                    .orElseThrow(() -> new TemplateDomainException(
                            "VERSION_NOT_FOUND", "Missing template version for render"));
            version.assertPublished();
            var design = version.designSchema()
                    .orElseThrow(() -> new TemplateDomainException("INVALID_DESIGN", "Published version missing design"));

            DocumentRenderer.RenderedBytes rendered = renderer.render(design, job.contentJson(), job.format());
            String key = TemplateObjectKeys.renderedDocument(
                    job.tenantId(), job.templateVersionId(), job.id(), job.format());

            Map<String, String> metadata = new HashMap<>();
            metadata.put("tenant-id", job.tenantId().value().toString());
            metadata.put("render-job-id", job.id().value().toString());
            metadata.put("template-version-id", job.templateVersionId().value().toString());
            metadata.put("content-hash-sha256", job.contentHash().sha256Hex());
            metadata.put("immutable", "true");

            objectStorage.put(ObjectPutRequest.builder()
                    .key(key)
                    .content(new ByteArrayInputStream(rendered.bytes()))
                    .contentLength(rendered.bytes().length)
                    .contentType(rendered.contentType())
                    .metadata(metadata)
                    .immutable(true)
                    .build());

            RenderedDocument document = RenderedDocument.builder()
                    .id(RenderedDocumentId.random())
                    .tenantId(job.tenantId())
                    .renderJobId(job.id())
                    .noteId(job.noteId())
                    .templateVersionId(job.templateVersionId())
                    .format(job.format())
                    .contentHash(job.contentHash())
                    .storageKey(key)
                    .sizeBytes(rendered.bytes().length)
                    .createdAt(clock.now())
                    .build();
            documentRepository.save(document);

            job.markCompleted(document.id(), clock.now());
            jobRepository.save(job);

            log.info(
                    "Render completed jobId={} tenantId={} format={} sizeBytes={} key={}",
                    job.id().value(),
                    job.tenantId().value(),
                    job.format(),
                    document.sizeBytes(),
                    key);
        } catch (ObjectStorageException | TemplateDomainException ex) {
            fail(job, ex);
            throw ex;
        } catch (RuntimeException ex) {
            fail(job, ex);
            throw ex;
        }
    }

    private void fail(RenderJob job, Exception ex) {
        job.markFailed(ex.getMessage(), clock.now());
        jobRepository.save(job);
        log.warn(
                "Render failed jobId={} attempt={}/{} codeOrMessage={}",
                job.id().value(),
                job.attemptCount(),
                job.maxAttempts(),
                ex.getMessage());
    }
}
