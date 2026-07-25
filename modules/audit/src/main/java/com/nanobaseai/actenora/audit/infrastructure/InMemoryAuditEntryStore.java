package com.nanobaseai.actenora.audit.infrastructure;

import com.nanobaseai.actenora.audit.application.port.AuditEntryStore;
import com.nanobaseai.actenora.audit.domain.AuditEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryAuditEntryStore implements AuditEntryStore {

    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public AuditEntry append(AuditEntry entry) {
        entries.add(entry);
        return entry;
    }

    @Override
    public List<AuditEntry> timeline(UUID tenantId, UUID resourceId) {
        return entries.stream()
                .filter(e -> e.tenantId().equals(tenantId))
                .filter(e -> e.resourceId().equals(resourceId))
                .sorted(Comparator.comparing(AuditEntry::occurredAt))
                .toList();
    }

    @Override
    public List<AuditEntry> listByTenant(UUID tenantId) {
        return entries.stream()
                .filter(e -> e.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(AuditEntry::occurredAt))
                .toList();
    }

    public List<AuditEntry> all() {
        return List.copyOf(entries);
    }
}
