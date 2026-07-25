package com.nanobaseai.actenora.aiprocessing.domain.job;

/**
 * Capability tags requested by AI jobs (aligned with model-management registry).
 */
public enum AiCapability {
    TRANSCRIPT_EXTRACTION,
    SUMMARIZATION,
    DECISION_EXTRACTION,
    ACTION_EXTRACTION,
    RISK_EXTRACTION,
    VALIDATION,
    FINAL_NOTE,
    TRANSLATION,
    EMBEDDING,
    RERANKING,
    RELATION_DETECTION,
    CONTRADICTION_DETECTION
}
