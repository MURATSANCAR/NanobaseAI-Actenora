package com.nanobaseai.actenora.delivery.application;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.delivery.application.port.DeliveryAuditPort;
import com.nanobaseai.actenora.delivery.application.port.DeliveryOrderRepository;
import com.nanobaseai.actenora.delivery.domain.DeliveryOrder;
import com.nanobaseai.actenora.delivery.domain.exception.ExternalDeliveryBlockedException;
import com.nanobaseai.actenora.delivery.infrastructure.audit.RecordingDeliveryAuditPort;
import com.nanobaseai.actenora.delivery.infrastructure.persistence.InMemoryDeliveryOrderRepository;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * External delivery is allowed only for an approval that is GRANTED for the target note version.
 * Orders are persisted and idempotent on (tenant, approval, noteVersion, channel).
 */
public final class ExternalDeliveryService {

    private final ApprovalApi approvalApi;
    private final DeliveryOrderRepository orders;
    private final DeliveryAuditPort auditPort;
    private final Clock clock;

    public ExternalDeliveryService(ApprovalApi approvalApi, Clock clock) {
        this(approvalApi, new InMemoryDeliveryOrderRepository(), new RecordingDeliveryAuditPort(), clock);
    }

    public ExternalDeliveryService(
            ApprovalApi approvalApi,
            DeliveryOrderRepository orders,
            DeliveryAuditPort auditPort,
            Clock clock
    ) {
        this.approvalApi = Objects.requireNonNull(approvalApi, "approvalApi");
        this.orders = Objects.requireNonNull(orders, "orders");
        this.auditPort = Objects.requireNonNull(auditPort, "auditPort");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public DeliveryOrder requestExternalDelivery(
            UUID tenantId,
            ApprovalId approvalId,
            UUID noteVersionId,
            String channel
    ) {
        Objects.requireNonNull(approvalId, "approvalId");
        Objects.requireNonNull(noteVersionId, "noteVersionId");
        Objects.requireNonNull(channel, "channel");
        if (channel.isBlank()) {
            throw new IllegalArgumentException("channel must not be blank");
        }

        TenantId tid = TenantId.of(tenantId);
        Optional<DeliveryOrder> existing = orders.findByKey(tid, approvalId, noteVersionId, channel);
        if (existing.isPresent()) {
            return existing.get();
        }

        if (!approvalApi.isGrantedForSubject(tenantId, approvalId, noteVersionId)) {
            throw new ExternalDeliveryBlockedException(
                    noteVersionId,
                    "approval " + approvalId.value() + " is not granted for this version"
            );
        }

        Instant now = clock.instant();
        DeliveryOrder order = DeliveryOrder.ready(tid, approvalId, noteVersionId, channel.trim(), now);
        DeliveryOrder saved = orders.save(order);
        auditPort.record(
                tenantId,
                "system:delivery",
                "DELIVERY_ORDER_READY",
                "DeliveryOrder",
                saved.id(),
                Map.of(
                        "approvalId", approvalId.value().toString(),
                        "noteVersionId", noteVersionId.toString(),
                        "channel", saved.channel(),
                        "status", saved.status().name()
                ),
                now
        );
        return saved;
    }

    public Optional<DeliveryOrder> findOrder(UUID tenantId, UUID orderId) {
        return orders.findById(TenantId.of(tenantId), orderId);
    }
}
