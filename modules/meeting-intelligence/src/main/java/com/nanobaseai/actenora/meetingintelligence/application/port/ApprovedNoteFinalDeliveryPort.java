package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.UUID;

/**
 * Post-approval hook for final HTML/PDF + external delivery (composition root).
 */
@FunctionalInterface
public interface ApprovedNoteFinalDeliveryPort {

    void onApproved(
            TenantId tenantId,
            UUID meetingOccurrenceId,
            UUID noteId,
            UUID noteVersionId,
            ApprovalId approvalId,
            String executiveSummary
    );

    static ApprovedNoteFinalDeliveryPort noop() {
        return (tenantId, meetingOccurrenceId, noteId, noteVersionId, approvalId, executiveSummary) -> {
        };
    }
}
