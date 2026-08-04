package com.nanobaseai.actenora.aiprocessing.domain.routing;

/**
 * How VALIDATION tasks pick a model role.
 */
public enum ValidationModelPreference {
    /** Use the primary quality role (default). Capability-oriented, not vendor-tied. */
    PRIMARY_QUALITY,
    /** Use a separate validation model role when available. */
    SEPARATE_VALIDATION_MODEL
}
