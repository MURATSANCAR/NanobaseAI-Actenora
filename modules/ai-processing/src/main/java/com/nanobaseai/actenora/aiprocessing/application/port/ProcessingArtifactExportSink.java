package com.nanobaseai.actenora.aiprocessing.application.port;

import com.nanobaseai.actenora.aiprocessing.domain.job.ProcessingArtifact;

/** Optional side-channel export for operational/evaluation artifacts. */
@FunctionalInterface
public interface ProcessingArtifactExportSink {

    void export(ProcessingArtifact artifact);

    static ProcessingArtifactExportSink noop() {
        return artifact -> { };
    }
}
