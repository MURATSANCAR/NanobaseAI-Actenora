package com.nanobaseai.actenora.delivery.infrastructure.approval;

import com.nanobaseai.actenora.approval.api.ApprovalApi;
import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.delivery.application.port.NoteApprovalGatePort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;
import java.util.UUID;

/**
 * Bridges ApprovalApi into delivery without importing approval.domain.
 */
public final class ApprovalApiNoteApprovalGate implements NoteApprovalGatePort {

    private final ApprovalApi approvalApi;

    public ApprovalApiNoteApprovalGate(ApprovalApi approvalApi) {
        this.approvalApi = Objects.requireNonNull(approvalApi, "approvalApi");
    }

    @Override
    public boolean isNoteVersionApproved(TenantId tenantId, ApprovalId approvalId, UUID noteVersionId) {
        return approvalApi.isGrantedForSubject(tenantId.value(), approvalId, noteVersionId);
    }
}
