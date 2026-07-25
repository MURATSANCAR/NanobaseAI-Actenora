package com.nanobaseai.actenora.tenant.infrastructure.persistence;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.tenant.application.port.TenantRepositoryPort;
import com.nanobaseai.actenora.tenant.domain.Tenant;
import com.nanobaseai.actenora.tenant.domain.TenantMembership;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTenantRepository implements TenantRepositoryPort {

    private final Map<UUID, Tenant> byId = new ConcurrentHashMap<>();
    private final Map<String, UUID> byEntraTenantId = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> memberships = new ConcurrentHashMap<>();

    @Override
    public Optional<Tenant> findById(TenantId tenantId) {
        return Optional.ofNullable(byId.get(tenantId.value()));
    }

    @Override
    public Optional<Tenant> findByEntraTenantId(String entraTenantId) {
        UUID id = byEntraTenantId.get(entraTenantId);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public void save(Tenant tenant) {
        UUID id = tenant.id().value();
        Tenant previous = byId.put(id, tenant);
        if (previous != null && !previous.entraTenantId().equals(tenant.entraTenantId())) {
            byEntraTenantId.remove(previous.entraTenantId());
        }
        byEntraTenantId.put(tenant.entraTenantId(), id);
        memberships.computeIfAbsent(id, ignored -> ConcurrentHashMap.newKeySet());
    }

    @Override
    public boolean isMember(TenantId tenantId, UUID userId) {
        Set<UUID> users = memberships.get(tenantId.value());
        return users != null && users.contains(userId);
    }

    @Override
    public void saveMembership(TenantMembership membership) {
        memberships
                .computeIfAbsent(membership.tenantId().value(), ignored -> ConcurrentHashMap.newKeySet())
                .add(membership.userId());
    }

    public void clear() {
        byId.clear();
        byEntraTenantId.clear();
        memberships.clear();
    }
}
