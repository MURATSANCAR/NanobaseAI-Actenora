package com.nanobaseai.actenora.policy.api;

import com.nanobaseai.actenora.policy.domain.QuotaDimension;
import com.nanobaseai.actenora.policy.domain.QuotaExceededException;
import com.nanobaseai.actenora.policy.domain.SlaLevel;
import com.nanobaseai.actenora.policy.domain.TenantPolicy;
import com.nanobaseai.actenora.policy.domain.TenantPolicyOverride;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

/**
 * Public façade / policy evaluation port for the Policy bounded context.
 * Cross-module callers use types in this package only.
 */
public interface PolicyApi {

    TenantPolicy evaluate(TenantId tenantId);

    void saveOverride(TenantPolicyOverride override);

    void assertWithinQuota(TenantId tenantId, QuotaDimension dimension, long requestedAmount)
            throws QuotaExceededException;

    void assertConcurrencyAvailable(TenantId tenantId) throws QuotaExceededException;

    boolean isModelAllowed(TenantId tenantId, String modelKey);

    boolean isCriticalMeetingFallbackAllowed(TenantId tenantId);

    SlaLevel resolveSlaLevel(TenantId tenantId, SlaLevel requestedOrNull);
}
