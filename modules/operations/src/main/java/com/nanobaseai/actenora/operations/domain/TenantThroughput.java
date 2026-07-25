package com.nanobaseai.actenora.operations.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;

/**
 * Per-tenant throughput sample for ops metrics.
 */
public record TenantThroughput(
        TenantId tenantId,
        long meetingsProcessed,
        long aiJobsCompleted,
        long deliveriesSucceeded
) {
    public TenantThroughput {
        Objects.requireNonNull(tenantId, "tenantId");
        if (meetingsProcessed < 0 || aiJobsCompleted < 0 || deliveriesSucceeded < 0) {
            throw new IllegalArgumentException("counts must be non-negative");
        }
    }
}
