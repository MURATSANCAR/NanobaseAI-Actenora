package com.nanobaseai.actenora.delivery.domain;

import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Delivery order bound to an approval id and an approved note version (ADR-010).
 */
public final class DeliveryOrder {

    private final UUID id;
    private final TenantId tenantId;
    private final ApprovalId approvalId;
    private final UUID noteVersionId;
    private final String channel;
    private final DeliveryOrderStatus status;
    private final Instant createdAt;

    private DeliveryOrder(
            UUID id,
            TenantId tenantId,
            ApprovalId approvalId,
            UUID noteVersionId,
            String channel,
            DeliveryOrderStatus status,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.approvalId = Objects.requireNonNull(approvalId, "approvalId");
        this.noteVersionId = Objects.requireNonNull(noteVersionId, "noteVersionId");
        this.channel = requireText(channel, "channel");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static DeliveryOrder rehydrate(
            UUID id,
            TenantId tenantId,
            ApprovalId approvalId,
            UUID noteVersionId,
            String channel,
            DeliveryOrderStatus status,
            Instant createdAt
    ) {
        return new DeliveryOrder(id, tenantId, approvalId, noteVersionId, channel, status, createdAt);
    }

    public static DeliveryOrder ready(
            TenantId tenantId,
            ApprovalId approvalId,
            UUID noteVersionId,
            String channel,
            Instant now
    ) {
        return new DeliveryOrder(
                UUID.randomUUID(), tenantId, approvalId, noteVersionId, channel,
                DeliveryOrderStatus.READY, now
        );
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public ApprovalId approvalId() { return approvalId; }
    public UUID noteVersionId() { return noteVersionId; }
    public String channel() { return channel; }
    public DeliveryOrderStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
}
