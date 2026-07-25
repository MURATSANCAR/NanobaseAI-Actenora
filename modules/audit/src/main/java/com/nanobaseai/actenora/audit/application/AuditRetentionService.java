package com.nanobaseai.actenora.audit.application;

import com.nanobaseai.actenora.audit.application.port.AuditEntryStore;
import com.nanobaseai.actenora.audit.domain.AuditEntry;
import com.nanobaseai.actenora.audit.domain.AuditRetentionPolicy;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * FAZ 27 — identifies audit entries eligible for cold-archive export.
 * Never deletes; immutability remains enforced by the append-only store.
 */
public final class AuditRetentionService {

    private final AuditEntryStore store;
    private final AuditRetentionPolicy policy;
    private final InstantClock clock;

    public AuditRetentionService(
            AuditEntryStore store,
            AuditRetentionPolicy policy,
            InstantClock clock
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<AuditEntry> listEligibleForArchive(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        var now = clock.now();
        return store.listByTenant(tenantId).stream()
                .filter(e -> policy.isEligibleForArchive(e.occurredAt(), now))
                .toList();
    }

    public AuditRetentionPolicy policy() {
        return policy;
    }
}
