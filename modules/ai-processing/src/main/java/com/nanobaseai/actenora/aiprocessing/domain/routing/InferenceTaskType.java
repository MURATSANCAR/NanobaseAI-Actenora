package com.nanobaseai.actenora.aiprocessing.domain.routing;

/**
 * Pipeline task types that drive role selection.
 */
public enum InferenceTaskType {
    CHUNK_EXTRACTION,
    CANDIDATE_MERGE,
    FINAL_NOTE,
    VALIDATION
}
