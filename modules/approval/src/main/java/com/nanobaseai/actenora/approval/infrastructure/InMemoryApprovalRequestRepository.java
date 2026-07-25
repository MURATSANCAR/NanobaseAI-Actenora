package com.nanobaseai.actenora.approval.infrastructure;

import com.nanobaseai.actenora.approval.application.port.ApprovalRequestRepository;
import com.nanobaseai.actenora.approval.domain.ApprovalRequest;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryApprovalRequestRepository implements ApprovalRequestRepository {

    private final Map<UUID, ApprovalRequest> byId = new ConcurrentHashMap<>();

    @Override
    public ApprovalRequest save(ApprovalRequest request) {
        byId.put(request.id(), request);
        return request;
    }

    @Override
    public Optional<ApprovalRequest> findById(TenantId tenantId, UUID approvalRequestId) {
        return Optional.ofNullable(byId.get(approvalRequestId))
                .filter(r -> r.tenantId().equals(tenantId));
    }

    @Override
    public Optional<ApprovalRequest> findBySubject(TenantId tenantId, UUID subjectId) {
        return byId.values().stream()
                .filter(r -> r.tenantId().equals(tenantId))
                .filter(r -> r.subjectId().equals(subjectId))
                .findFirst();
    }
}
