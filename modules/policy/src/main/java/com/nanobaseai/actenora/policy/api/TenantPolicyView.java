package com.nanobaseai.actenora.policy.api;

import com.nanobaseai.actenora.policy.domain.ConcurrencyPolicy;
import com.nanobaseai.actenora.policy.domain.DeliveryPolicy;
import com.nanobaseai.actenora.policy.domain.ExternalParticipantPolicy;
import com.nanobaseai.actenora.policy.domain.ModelAccessPolicy;
import com.nanobaseai.actenora.policy.domain.ProcessingSlaPolicy;
import com.nanobaseai.actenora.policy.domain.QuotaLimits;
import com.nanobaseai.actenora.policy.domain.RetentionPolicy;
import com.nanobaseai.actenora.policy.domain.TenantPolicy;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

public record TenantPolicyView(
        TenantId tenantId,
        RetentionPolicy retention,
        DeliveryPolicy delivery,
        ModelAccessPolicy modelAccess,
        ProcessingSlaPolicy processingSla,
        ConcurrencyPolicy concurrency,
        ExternalParticipantPolicy externalParticipant,
        QuotaLimits quotas,
        long version
) {
    public static TenantPolicyView from(TenantPolicy policy) {
        return new TenantPolicyView(
                policy.tenantId(),
                policy.retention(),
                policy.delivery(),
                policy.modelAccess(),
                policy.processingSla(),
                policy.concurrency(),
                policy.externalParticipant(),
                policy.quotas(),
                policy.version()
        );
    }
}
