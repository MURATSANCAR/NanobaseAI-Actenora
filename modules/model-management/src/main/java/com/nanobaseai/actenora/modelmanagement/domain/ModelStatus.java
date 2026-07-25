package com.nanobaseai.actenora.modelmanagement.domain;

/**
 * Lifecycle status of a registered model definition.
 */
public enum ModelStatus {
    ENABLED,
    DISABLED,
    DRAINING,
    RETIRED;

    public boolean acceptsNewWork() {
        return this == ENABLED;
    }
}
