package com.nanobaseai.actenora.delivery.application;

import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.delivery.domain.DeliveryPolicySnapshot;
import com.nanobaseai.actenora.delivery.domain.DeliveryRecipient;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Enqueue command. Each recipient becomes an isolated delivery request (especially externals).
 */
public record EnqueueDeliveryCommand(
        TenantId tenantId,
        UUID noteVersionId,
        ApprovalId approvalId,
        List<DeliveryRecipient> recipients,
        DeliveryPolicySnapshot policySnapshot,
        String subject,
        String bodyText,
        String actorId
) {

    public EnqueueDeliveryCommand {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(noteVersionId, "noteVersionId");
        Objects.requireNonNull(approvalId, "approvalId");
        Objects.requireNonNull(recipients, "recipients");
        Objects.requireNonNull(policySnapshot, "policySnapshot");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(bodyText, "bodyText");
        Objects.requireNonNull(actorId, "actorId");
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("at least one recipient is required");
        }
        recipients = List.copyOf(recipients);
    }
}
