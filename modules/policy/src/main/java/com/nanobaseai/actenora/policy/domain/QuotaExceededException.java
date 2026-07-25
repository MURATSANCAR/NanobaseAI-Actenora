package com.nanobaseai.actenora.policy.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.error.ActenoraException;

import java.util.Objects;

/** Raised when a tenant would exceed a configured capacity limit. */
public final class QuotaExceededException extends ActenoraException {

    private final TenantId tenantId;
    private final QuotaDimension dimension;
    private final long limit;
    private final long used;
    private final long requested;

    public QuotaExceededException(
            TenantId tenantId,
            QuotaDimension dimension,
            long limit,
            long used,
            long requested
    ) {
        super(
                "QUOTA_EXCEEDED",
                "quota exceeded for " + dimension + " (used=" + used + ", requested=" + requested + ", limit=" + limit + ")"
        );
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.limit = limit;
        this.used = used;
        this.requested = requested;
    }

    public TenantId tenantId() { return tenantId; }
    public QuotaDimension dimension() { return dimension; }
    public long limit() { return limit; }
    public long used() { return used; }
    public long requested() { return requested; }
}
