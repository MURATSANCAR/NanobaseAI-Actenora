package com.nanobaseai.actenora.aiprocessing.domain.routing;

/**
 * Logical local model roles used by multi-model routing.
 * Catalog / runtime ids are resolved behind these roles — callers never pick vendor strings.
 * Physical model identity (e.g. Qwen) must stay in model-management catalog / adapters only.
 */
public enum ModelRole {
    /** Fast path for chunk extraction (may be a mock deployment when no second physical model exists). */
    FAST_EXTRACTION,
    /** Primary quality model for merge / final note (and default validation). Capability role — not vendor-tied. */
    PRIMARY_QUALITY,
    /** Optional dedicated validation model when tenant policy opts in. */
    VALIDATION
}
