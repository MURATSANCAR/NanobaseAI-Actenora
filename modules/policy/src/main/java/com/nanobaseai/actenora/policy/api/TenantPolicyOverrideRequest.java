package com.nanobaseai.actenora.policy.api;

import com.nanobaseai.actenora.policy.domain.ConcurrencyPolicy;
import com.nanobaseai.actenora.policy.domain.DeliveryPolicy;
import com.nanobaseai.actenora.policy.domain.ExternalParticipantPolicy;
import com.nanobaseai.actenora.policy.domain.ModelAccessPolicy;
import com.nanobaseai.actenora.policy.domain.ProcessingSlaPolicy;
import com.nanobaseai.actenora.policy.domain.QuotaLimits;
import com.nanobaseai.actenora.policy.domain.RetentionPolicy;

/**
 * Partial override payload. Null fields mean "keep current / default".
 */
public record TenantPolicyOverrideRequest(
        RetentionPolicy retention,
        DeliveryPolicy delivery,
        ModelAccessPolicy modelAccess,
        ProcessingSlaPolicy processingSla,
        ConcurrencyPolicy concurrency,
        ExternalParticipantPolicy externalParticipant,
        QuotaLimits quotas
) {
}
