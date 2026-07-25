package com.nanobaseai.actenora.identity.domain;

import java.util.UUID;

public final class OptimisticLockException extends RuntimeException {

    private final UUID resourceId;
    private final long expectedVersion;
    private final long actualVersion;

    public OptimisticLockException(UUID resourceId, long expectedVersion, long actualVersion) {
        super("Optimistic lock failure for " + resourceId
                + " expectedVersion=" + expectedVersion
                + " actualVersion=" + actualVersion);
        this.resourceId = resourceId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    public UUID resourceId() { return resourceId; }
    public long expectedVersion() { return expectedVersion; }
    public long actualVersion() { return actualVersion; }
}
