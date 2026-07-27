package com.nanobaseai.actenora.aiprocessing.application.pipeline.staged;

/**
 * Rollout mode for AI extraction orchestration.
 */
public enum PipelineMode {
    /** Monolithic ExtractionPipelineService inside one CHUNK_EXTRACTION job. */
    LEGACY,
    /** Stage jobs + dependency DAG (+ optional Rabbit stage queues). */
    STAGED;

    public static PipelineMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return STAGED;
        }
        return PipelineMode.valueOf(raw.trim().toUpperCase());
    }
}
