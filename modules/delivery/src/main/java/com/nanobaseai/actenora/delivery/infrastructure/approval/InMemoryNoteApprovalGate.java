package com.nanobaseai.actenora.delivery.infrastructure.approval;

import com.nanobaseai.actenora.approval.api.ApprovalId;
import com.nanobaseai.actenora.delivery.application.port.NoteApprovalGatePort;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory approval gate for tests / local composition until ApprovalApi methods are wired.
 */
public final class InMemoryNoteApprovalGate implements NoteApprovalGatePort {

    private final Set<String> granted = ConcurrentHashMap.newKeySet();

    public void grant(TenantId tenantId, ApprovalId approvalId, UUID noteVersionId) {
        granted.add(key(tenantId, approvalId, noteVersionId));
    }

    public void revoke(TenantId tenantId, ApprovalId approvalId, UUID noteVersionId) {
        granted.remove(key(tenantId, approvalId, noteVersionId));
    }

    @Override
    public boolean isNoteVersionApproved(TenantId tenantId, ApprovalId approvalId, UUID noteVersionId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(approvalId, "approvalId");
        Objects.requireNonNull(noteVersionId, "noteVersionId");
        return granted.contains(key(tenantId, approvalId, noteVersionId));
    }

    private static String key(TenantId tenantId, ApprovalId approvalId, UUID noteVersionId) {
        return tenantId.value() + "|" + approvalId.value() + "|" + noteVersionId;
    }
}
