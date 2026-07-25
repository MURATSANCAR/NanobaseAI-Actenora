package ai.nanobase.actenora.policy.api;

import ai.nanobase.actenora.policy.domain.QuotaDimension;
import ai.nanobase.actenora.policy.domain.QuotaExceededException;
import ai.nanobase.actenora.policy.domain.SlaLevel;
import ai.nanobase.actenora.policy.domain.TenantPolicy;
import ai.nanobase.actenora.policy.domain.TenantPolicyOverride;

import java.util.UUID;

/**
 * Published evaluation port for other bounded contexts.
 * Cross-module consumers must depend on this interface only.
 */
public interface PolicyEvaluationPort {

    TenantPolicy evaluate(UUID tenantId);

    void saveOverride(TenantPolicyOverride override);

    void assertWithinQuota(UUID tenantId, QuotaDimension dimension, long requestedAmount)
            throws QuotaExceededException;

    void assertConcurrencyAvailable(UUID tenantId) throws QuotaExceededException;

    boolean isModelAllowed(UUID tenantId, String modelKey);

    boolean isCriticalMeetingFallbackAllowed(UUID tenantId);

    SlaLevel resolveSlaLevel(UUID tenantId, SlaLevel requestedOrNull);
}
