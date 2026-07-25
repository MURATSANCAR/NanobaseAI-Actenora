package com.nanobaseai.actenora.delivery.application.port;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

/**
 * Per-tenant / per-provider rate limiter for outbound mail.
 */
public interface DeliveryRateLimiter {

    /**
     * @return true if a send slot was acquired
     */
    boolean tryAcquire(TenantId tenantId, String providerType);

    void release(TenantId tenantId, String providerType);
}
