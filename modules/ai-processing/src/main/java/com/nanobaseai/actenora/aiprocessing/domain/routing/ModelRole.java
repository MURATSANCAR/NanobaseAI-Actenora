package com.nanobaseai.actenora.aiprocessing.domain.routing;

/**
 * Logical local model roles used by multi-model routing.
 * Catalog / runtime ids are resolved behind these roles — callers never pick vendor strings.
 */
public enum ModelRole {
    /** Fast path for chunk extraction (may be a mock deployment when no second physical model exists). */
    FAST_EXTRACTION,
    /** Primary quality model for merge / final note (and default validation). */
    QWEN27_FINAL,
    /** Optional dedicated validation model when tenant policy opts in. */
    VALIDATION
}
