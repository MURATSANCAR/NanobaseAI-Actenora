package com.nanobaseai.actenora.audit.infrastructure.persistence;

import com.nanobaseai.actenora.audit.application.AuditRepositoryPort;
import com.nanobaseai.actenora.audit.domain.AuditEntry;
import com.nanobaseai.actenora.audit.domain.AuditImmutabilityException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Append-only in-memory store mirroring immutable PostgreSQL audit.entries behaviour. */
public final class InMemoryAppendOnlyAuditRepository implements AuditRepositoryPort {

    private final Map<UUID, AuditEntry> byId = new ConcurrentHashMap<>();

    @Override
    public void append(AuditEntry entry) {
        AuditEntry previous = byId.putIfAbsent(entry.id(), entry);
        if (previous != null) {
            throw new AuditImmutabilityException("audit entry already exists and cannot be overwritten: " + entry.id());
        }
    }

    @Override
    public Optional<AuditEntry> findById(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<AuditEntry> findByTenantId(UUID tenantId) {
        List<AuditEntry> result = new ArrayList<>();
        for (AuditEntry entry : byId.values()) {
            if (entry.tenantId().equals(tenantId)) {
                result.add(entry);
            }
        }
        result.sort((a, b) -> a.occurredAt().compareTo(b.occurredAt()));
        return List.copyOf(result);
    }

    public void update(AuditEntry entry) {
        throw new AuditImmutabilityException("audit entries cannot be updated");
    }

    public void delete(UUID id) {
        throw new AuditImmutabilityException("audit entries cannot be deleted");
    }

    public int size() {
        return byId.size();
    }
}
