package com.nanobaseai.actenora.approval.application.port;

import com.nanobaseai.actenora.approval.domain.ApprovalRequest;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRequestRepository {

    ApprovalRequest save(ApprovalRequest request);

    Optional<ApprovalRequest> findById(TenantId tenantId, UUID approvalRequestId);

    Optional<ApprovalRequest> findBySubject(TenantId tenantId, UUID subjectId);

    List<ApprovalRequest> listByTenant(TenantId tenantId);
}
