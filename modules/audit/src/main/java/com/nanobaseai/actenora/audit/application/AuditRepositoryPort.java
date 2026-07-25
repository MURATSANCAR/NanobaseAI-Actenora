package com.nanobaseai.actenora.audit.application;

import com.nanobaseai.actenora.audit.domain.AuditEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Append-only audit persistence. Update and delete must never be offered. */
public interface AuditRepositoryPort {
    void append(AuditEntry entry);
    Optional<AuditEntry> findById(UUID id);
    List<AuditEntry> findByTenantId(UUID tenantId);
}
