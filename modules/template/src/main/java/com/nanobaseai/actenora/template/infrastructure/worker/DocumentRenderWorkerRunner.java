package com.nanobaseai.actenora.template.infrastructure.worker;

import com.nanobaseai.actenora.template.application.DocumentRenderWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Polling runner for the document-renderer worker profile.
 * Extractable later into services/document-renderer.
 */
public class DocumentRenderWorkerRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentRenderWorkerRunner.class);

    private final DocumentRenderWorker worker;
    private final int batchSize;

    public DocumentRenderWorkerRunner(DocumentRenderWorker worker, int batchSize) {
        this.worker = worker;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${actenora.template.worker.poll-interval-ms:2000}")
    public void poll() {
        int processed = worker.processPending(batchSize);
        if (processed > 0) {
            log.info("Document render worker processed {} job(s)", processed);
        }
    }
}
