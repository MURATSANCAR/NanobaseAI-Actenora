package com.nanobaseai.actenora.delivery.application.port;

import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.delivery.domain.DeliveryOrder;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for approval-gated delivery orders (FAZ 19).
 */
public interface DeliveryOrderRepository {

    DeliveryOrder save(DeliveryOrder order);

    Optional<DeliveryOrder> findById(TenantId tenantId, UUID orderId);

    Optional<DeliveryOrder> findByKey(
            TenantId tenantId,
            ApprovalId approvalId,
            UUID noteVersionId,
            String channel
    );
}
