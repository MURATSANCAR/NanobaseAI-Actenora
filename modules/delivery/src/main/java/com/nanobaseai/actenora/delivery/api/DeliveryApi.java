package com.nanobaseai.actenora.delivery.api;

import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.delivery.application.EnqueueDeliveryCommand;
import com.nanobaseai.actenora.delivery.application.EnqueueDeliveryResult;
import com.nanobaseai.actenora.delivery.domain.DeliveryStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Optional;
import java.util.UUID;

/**
 * Public façade for the Delivery bounded context.
 * Cross-module callers use types in this package only.
 */
public interface DeliveryApi {

    /**
     * FAZ 19 gate: creates (or returns existing) READY delivery order when approval is granted
     * for the note version. Idempotent on (tenant, approvalId, noteVersionId, channel).
     */
    DeliveryOrderView requestExternalDelivery(
            UUID tenantId,
            ApprovalId approvalId,
            UUID noteVersionId,
            String channel
    );

    Optional<DeliveryOrderView> getOrder(UUID tenantId, UUID orderId);

    /**
     * FAZ 20: enqueue idempotent per-recipient mail dispatches for an approved note version.
     */
    EnqueueDeliveryResult enqueue(EnqueueDeliveryCommand command);

    Optional<DeliveryStatus> status(TenantId tenantId, DeliveryRequestId id);

    /**
     * Promotes {@code PROVIDER_ACCEPTED} → {@code DELIVERED} after provider confirmation.
     * Acceptance alone must never imply delivery.
     */
    DeliveryStatus confirmDelivered(TenantId tenantId, DeliveryRequestId id);
}
