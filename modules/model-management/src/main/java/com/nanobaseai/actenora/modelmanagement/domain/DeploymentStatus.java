package com.nanobaseai.actenora.modelmanagement.domain;

/**
 * Runtime status of a model deployment node.
 */
public enum DeploymentStatus {
    REGISTERED,
    HEALTHY,
    UNHEALTHY,
    DRAINING,
    OFFLINE;

    public boolean acceptsNewWork() {
        return this == HEALTHY || this == REGISTERED;
    }
}
