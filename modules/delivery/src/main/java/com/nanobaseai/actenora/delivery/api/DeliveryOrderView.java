package com.nanobaseai.actenora.delivery.api;

import com.nanobaseai.actenora.delivery.domain.DeliveryOrder;
import com.nanobaseai.actenora.delivery.domain.DeliveryOrderStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant-scoped delivery order snapshot for HTTP / cross-module consumers.
 */
public record DeliveryOrderView(
        UUID id,
        UUID tenantId,
        UUID approvalId,
        UUID noteVersionId,
        String channel,
        DeliveryOrderStatus status,
        Instant createdAt
) {
    public DeliveryOrderView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(approvalId, "approvalId");
        Objects.requireNonNull(noteVersionId, "noteVersionId");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static DeliveryOrderView from(DeliveryOrder order) {
        Objects.requireNonNull(order, "order");
        return new DeliveryOrderView(
                order.id(),
                order.tenantId().value(),
                order.approvalId().value(),
                order.noteVersionId(),
                order.channel(),
                order.status(),
                order.createdAt()
        );
    }
}
