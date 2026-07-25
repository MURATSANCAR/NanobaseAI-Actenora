package com.nanobaseai.actenora.approval.domain;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.UUID;

/**
 * Approval request aggregate. Internal — delivery must not import this type.
 */
public class ApprovalRequestEntity {

    private final UUID id;
    private final TenantId tenantId;
    private ApprovalStatus status;

    public ApprovalRequestEntity(UUID id, TenantId tenantId, ApprovalStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.status = status;
    }

    public UUID id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public ApprovalStatus status() {
        return status;
    }
}
