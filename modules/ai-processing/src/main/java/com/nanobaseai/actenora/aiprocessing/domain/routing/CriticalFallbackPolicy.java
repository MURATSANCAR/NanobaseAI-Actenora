package com.nanobaseai.actenora.aiprocessing.domain.routing;

/**
 * Critical-job rules for quality-downgrade fallback.
 */
public final class CriticalFallbackPolicy {

    private CriticalFallbackPolicy() {
    }

    /**
     * @return true when selecting a lower-quality alternate model is forbidden for this request
     */
    public static boolean forbidsQualityDowngrade(RoutingRequest request, TenantRoutingPolicy policy) {
        if (!request.critical()) {
            return !policy.allowQualityDowngrade();
        }
        return policy.criticalJobsForbidDowngrade() || !policy.allowQualityDowngrade();
    }
}
