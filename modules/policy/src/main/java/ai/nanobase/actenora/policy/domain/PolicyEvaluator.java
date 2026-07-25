package ai.nanobase.actenora.policy.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Domain service that evaluates effective policy and enforces quotas/concurrency.
 */
public final class PolicyEvaluator {

    public TenantPolicy resolve(UUID tenantId, TenantPolicyOverride overrideOrNull) {
        TenantPolicy defaults = TenantPolicy.systemDefaultsFor(tenantId);
        if (overrideOrNull == null) {
            return defaults;
        }
        return defaults.apply(overrideOrNull);
    }

    public void assertWithinQuota(
            TenantPolicy policy,
            QuotaDimension dimension,
            long used,
            long requested
    ) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(dimension, "dimension");
        if (requested < 0) {
            throw new IllegalArgumentException("requested must be non-negative");
        }
        long limit = policy.quotas().limitOf(dimension);
        if (dimension == QuotaDimension.CONCURRENT_AI_JOBS) {
            limit = policy.concurrency().maxConcurrentAiJobs();
        }
        if (used + requested > limit) {
            throw new QuotaExceededException(policy.tenantId(), dimension, limit, used, requested);
        }
    }

    public void assertConcurrencyAvailable(TenantPolicy policy, int currentlyRunning) {
        assertWithinQuota(policy, QuotaDimension.CONCURRENT_AI_JOBS, currentlyRunning, 1);
    }

    public boolean isModelAllowed(TenantPolicy policy, String modelKey) {
        return policy.modelAccess().isModelAllowed(modelKey);
    }

    public boolean isCriticalMeetingFallbackAllowed(TenantPolicy policy) {
        return policy.modelAccess().criticalMeetingFallbackAllowed();
    }

    public SlaLevel resolveSlaLevel(TenantPolicy policy, SlaLevel requestedOrNull) {
        return requestedOrNull == null ? policy.processingSla().defaultLevel() : requestedOrNull;
    }
}
