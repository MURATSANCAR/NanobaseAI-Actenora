package com.nanobaseai.actenora.delivery.application;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.delivery.domain.DeliveryOrder;
import com.nanobaseai.actenora.delivery.domain.exception.ExternalDeliveryBlockedException;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * External delivery is allowed only for an approval that is GRANTED for the target note version.
 */
public final class ExternalDeliveryService {

    private final ApprovalApi approvalApi;
    private final Clock clock;

    public ExternalDeliveryService(ApprovalApi approvalApi, Clock clock) {
        this.approvalApi = Objects.requireNonNull(approvalApi, "approvalApi");
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

        if (!approvalApi.isGrantedForSubject(tenantId, approvalId, noteVersionId)) {
            throw new ExternalDeliveryBlockedException(
                    noteVersionId,
                    "approval " + approvalId.value() + " is not granted for this version"
            );
        }

        Instant now = clock.instant();
        return DeliveryOrder.ready(TenantId.of(tenantId), approvalId, noteVersionId, channel, now);
    }
}
