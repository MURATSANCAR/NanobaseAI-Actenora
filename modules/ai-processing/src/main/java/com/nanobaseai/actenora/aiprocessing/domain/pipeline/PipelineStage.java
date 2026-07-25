package com.nanobaseai.actenora.aiprocessing.domain.pipeline;

/**
 * Ordered stages of the production extraction pipeline.
 */
public enum PipelineStage {
    NORMALIZE,
    CHUNK,
    EXTRACT,
    MERGE,
    DETERMINISTIC_VALIDATE,
    FINAL_NOTE
}
