package com.nanobaseai.actenora.aiprocessing.domain.routing;

/**
 * How VALIDATION tasks pick a model role.
 */
public enum ValidationModelPreference {
    /** Use Qwen27FinalModel (default). */
    QWEN27_FINAL,
    /** Use a separate validation model role when available. */
    SEPARATE_VALIDATION_MODEL
}
