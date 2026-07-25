package ai.nanobase.actenora.policy.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Raised when a tenant would exceed a configured capacity limit.
 */
public final class QuotaExceededException extends RuntimeException {

    private final UUID tenantId;
    private final QuotaDimension dimension;
    private final long limit;
    private final long used;
    private final long requested;

    public QuotaExceededException(
            UUID tenantId,
            QuotaDimension dimension,
            long limit,
            long used,
            long requested
    ) {
        super("quota exceeded for " + dimension + " (used=" + used + ", requested=" + requested + ", limit=" + limit + ")");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.limit = limit;
        this.used = used;
        this.requested = requested;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public QuotaDimension dimension() {
        return dimension;
    }

    public long limit() {
        return limit;
    }

    public long used() {
        return used;
    }

    public long requested() {
        return requested;
    }
}
