package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

/**
 * Fail-closed pipeline error with a stable category fingerprint.
 */
public final class PipelineException extends ActenoraException {

    private final FailureCategory category;
    private final PipelineStage stage;
    private final String fingerprint;

    public PipelineException(
            FailureCategory category,
            PipelineStage stage,
            String message
    ) {
        super(category.name(), message);
        this.category = category;
        this.stage = stage;
        this.fingerprint = category.name() + "|" + stage.name() + "|" + normalize(message);
    }

    public FailureCategory category() {
        return category;
    }

    public PipelineStage stage() {
        return stage;
    }

    public String fingerprint() {
        return fingerprint;
    }

    private static String normalize(String message) {
        if (message == null) {
            return "";
        }
        return message.replaceAll("\\s+", " ").trim();
    }
}
