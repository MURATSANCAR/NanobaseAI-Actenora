package com.nanobaseai.actenora.aiprocessing.domain.routing;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Tenant-scoped routing constraints for multi-model fallback and shadow.
 */
public record TenantRoutingPolicy(
        UUID tenantId,
        Set<String> approvedAlternateModelKeys,
        boolean allowQualityDowngrade,
        boolean criticalJobsForbidDowngrade,
        ValidationModelPreference validationModelPreference,
        boolean shadowExecutionEnabled,
        ConsensusMode consensusMode
) {
    public TenantRoutingPolicy {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(approvedAlternateModelKeys, "approvedAlternateModelKeys");
        approvedAlternateModelKeys = Collections.unmodifiableSet(new LinkedHashSet<>(approvedAlternateModelKeys));
        Objects.requireNonNull(validationModelPreference, "validationModelPreference");
        Objects.requireNonNull(consensusMode, "consensusMode");
    }

    public boolean isAlternateApproved(String modelKey) {
        Objects.requireNonNull(modelKey, "modelKey");
        return approvedAlternateModelKeys.contains(modelKey);
    }

    public static TenantRoutingPolicy defaults(UUID tenantId) {
        return new TenantRoutingPolicy(
                tenantId,
                Set.of(),
                true,
                true,
                ValidationModelPreference.QWEN27_FINAL,
                false,
                ConsensusMode.OFF
        );
    }
}
