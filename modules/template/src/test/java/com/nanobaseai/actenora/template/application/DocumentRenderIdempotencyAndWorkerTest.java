package com.nanobaseai.actenora.template.application;

import com.nanobaseai.actenora.sharedkernel.port.storage.ObjectStorageException;
import com.nanobaseai.actenora.template.TemplateTestFixture;
import com.nanobaseai.actenora.template.domain.RenderFormat;
import com.nanobaseai.actenora.template.domain.RenderJob;
import com.nanobaseai.actenora.template.domain.RenderJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentRenderIdempotencyAndWorkerTest {

    private TemplateTestFixture fx;

    @BeforeEach
    void setUp() {
        fx = new TemplateTestFixture();
    }

    @Test
    void duplicateRenderReturnsSameJob() throws Exception {
        var published = fx.publishBasicTemplate();
        UUID noteId = UUID.randomUUID();
        String content = fx.sampleContentJson();

        RenderJob first = fx.renders.enqueue(fx.tenantId, noteId, published.versionId(), content, RenderFormat.HTML);
        RenderJob second = fx.renders.enqueue(fx.tenantId, noteId, published.versionId(), content, RenderFormat.HTML);

        assertEquals(first.id(), second.id());
        assertEquals(first.contentHash(), second.contentHash());
    }

    @Test
    void workerCompletesHtmlRenderAndStoresImmutableObject() throws Exception {
        var published = fx.publishBasicTemplate();
        UUID noteId = UUID.randomUUID();
        RenderJob job = fx.renders.enqueue(
                fx.tenantId, noteId, published.versionId(), fx.sampleContentJson(), RenderFormat.HTML);

        fx.worker.processPending(10);

        RenderJob completed = fx.jobs.findById(fx.tenantId, job.id()).orElseThrow();
        assertEquals(RenderJobStatus.COMPLETED, completed.status());
        assertTrue(completed.renderedDocumentId().isPresent());
        var document = fx.documents.findByJobId(fx.tenantId, job.id()).orElseThrow();
        assertTrue(fx.storage.exists(document.storageKey()));
        assertTrue(document.contentHash().sha256Hex().length() == 64);
    }

    @Test
    void workerRetriesAfterStorageFailureThenSucceeds() throws Exception {
        var published = fx.publishBasicTemplate();
        UUID noteId = UUID.randomUUID();
        RenderJob job = fx.renders.enqueue(
                fx.tenantId, noteId, published.versionId(), fx.sampleContentJson(), RenderFormat.HTML);

        fx.storage.forceTimeout(true);
        fx.worker.processPending(10);
        RenderJob afterFail = fx.jobs.findById(fx.tenantId, job.id()).orElseThrow();
        assertEquals(RenderJobStatus.PENDING, afterFail.status());
        assertEquals(1, afterFail.attemptCount());
        assertTrue(afterFail.lastError().isPresent());

        fx.storage.forceTimeout(false);
        fx.worker.processPending(10);
        RenderJob afterRetry = fx.jobs.findById(fx.tenantId, job.id()).orElseThrow();
        assertEquals(RenderJobStatus.COMPLETED, afterRetry.status());
        assertEquals(2, afterRetry.attemptCount());
    }

    @Test
    void storageFailureSurfacesAsObjectStorageTimeout() throws Exception {
        var published = fx.publishBasicTemplate();
        UUID noteId = UUID.randomUUID();
        RenderJob job = fx.renders.enqueue(
                fx.tenantId, noteId, published.versionId(), fx.sampleContentJson(), RenderFormat.HTML);

        fx.storage.forceTimeout(true);
        ObjectStorageException ex = assertThrows(
                ObjectStorageException.class,
                () -> fx.worker.processOne(job));
        assertEquals("OBJECT_STORAGE_TIMEOUT", ex.code());
    }
}
