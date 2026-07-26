package com.nanobaseai.actenora.delivery.infrastructure.persistence;

import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.delivery.application.port.DeliveryOrderRepository;
import com.nanobaseai.actenora.delivery.domain.DeliveryOrder;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory delivery order store with idempotent key lookup.
 */
public final class InMemoryDeliveryOrderRepository implements DeliveryOrderRepository {

    private final Map<String, DeliveryOrder> byId = new ConcurrentHashMap<>();
    private final Map<String, UUID> keyToId = new ConcurrentHashMap<>();

    @Override
    public DeliveryOrder save(DeliveryOrder order) {
        Objects.requireNonNull(order, "order");
        String idKey = idKey(order.tenantId(), order.id());
        byId.put(idKey, order);
        keyToId.put(naturalKey(order.tenantId(), order.approvalId(), order.noteVersionId(), order.channel()), order.id());
        return order;
    }

    @Override
    public Optional<DeliveryOrder> findById(TenantId tenantId, UUID orderId) {
        return Optional.ofNullable(byId.get(idKey(tenantId, orderId)));
    }

    @Override
    public Optional<DeliveryOrder> findByKey(
            TenantId tenantId,
            ApprovalId approvalId,
            UUID noteVersionId,
            String channel
    ) {
        UUID id = keyToId.get(naturalKey(tenantId, approvalId, noteVersionId, channel));
        if (id == null) {
            return Optional.empty();
        }
        return findById(tenantId, id);
    }

    private static String idKey(TenantId tenantId, UUID orderId) {
        return tenantId.value() + ":" + orderId;
    }

    private static String naturalKey(
            TenantId tenantId,
            ApprovalId approvalId,
            UUID noteVersionId,
            String channel
    ) {
        String normalizedChannel = channel == null ? "" : channel.trim().toLowerCase();
        return tenantId.value() + "|" + approvalId.value() + "|" + noteVersionId + "|" + normalizedChannel;
    }
}
