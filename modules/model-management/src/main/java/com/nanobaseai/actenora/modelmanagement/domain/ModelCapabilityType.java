package com.nanobaseai.actenora.modelmanagement.domain;

/**
 * Capability tags used for routing (FAZ 11 / FAZ 12).
 */
public enum ModelCapabilityType {
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
