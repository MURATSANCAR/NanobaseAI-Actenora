package com.nanobaseai.actenora.delivery.application.port;

import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.UUID;

/**
 * Gates external delivery on Approval BC without importing approval.domain.
 */
public interface NoteApprovalGatePort {

    boolean isNoteVersionApproved(TenantId tenantId, ApprovalId approvalId, UUID noteVersionId);
}
