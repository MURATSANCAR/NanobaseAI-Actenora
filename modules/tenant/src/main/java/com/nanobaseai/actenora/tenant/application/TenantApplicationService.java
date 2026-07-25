package com.nanobaseai.actenora.tenant.application;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.tenant.api.TenantApi;
import com.nanobaseai.actenora.tenant.api.TenantView;
import com.nanobaseai.actenora.tenant.application.port.TenantRepositoryPort;
import com.nanobaseai.actenora.tenant.domain.CrossTenantAccessException;
import com.nanobaseai.actenora.tenant.domain.Tenant;
import com.nanobaseai.actenora.tenant.domain.TenantMembership;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class TenantApplicationService implements TenantApi {

    private final TenantRepositoryPort repository;
    private final Clock clock;

    public TenantApplicationService(TenantRepositoryPort repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public TenantView requireActive(TenantId tenantId) {
        Tenant tenant = repository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant: " + tenantId.value()));
        tenant.assertActive();
        return toView(tenant);
    }

    @Override
    public Optional<TenantView> findById(TenantId tenantId) {
        return repository.findById(tenantId).map(TenantApplicationService::toView);
    }

    @Override
    public Optional<TenantView> findByEntraTenantId(String entraTenantId) {
        Objects.requireNonNull(entraTenantId, "entraTenantId");
        return repository.findByEntraTenantId(entraTenantId).map(TenantApplicationService::toView);
    }

    @Override
    public TenantView provision(
            String name,
            String entraTenantId,
            String timezone,
            String defaultLanguage,
            int retentionPolicyDays
    ) {
        repository.findByEntraTenantId(entraTenantId).ifPresent(existing -> {
            throw new DuplicateEntraTenantException(entraTenantId, existing.id());
        });
        Instant now = clock.instant();
        Tenant tenant = Tenant.provision(name, entraTenantId, timezone, defaultLanguage, retentionPolicyDays, now);
        repository.save(tenant);
        return toView(tenant);
    }

    @Override
    public TenantView suspend(TenantId tenantId, long expectedVersion) {
        Tenant tenant = requireExisting(tenantId);
        tenant.suspend(expectedVersion, clock.instant());
        repository.save(tenant);
        return toView(tenant);
    }

    @Override
    public TenantView activate(TenantId tenantId, long expectedVersion) {
        Tenant tenant = requireExisting(tenantId);
        tenant.activate(expectedVersion, clock.instant());
        repository.save(tenant);
        return toView(tenant);
    }

    @Override
    public boolean isMember(TenantId tenantId, UUID userId) {
        return repository.isMember(tenantId, userId);
    }

    @Override
    public void ensureMembership(TenantId tenantId, UUID userId) {
        requireExisting(tenantId);
        if (!repository.isMember(tenantId, userId)) {
            repository.saveMembership(new TenantMembership(tenantId, userId));
        }
    }

    @Override
    public void assertSameTenant(TenantId principalTenantId, TenantId resourceTenantId) {
        Objects.requireNonNull(principalTenantId, "principalTenantId");
        Objects.requireNonNull(resourceTenantId, "resourceTenantId");
        if (!principalTenantId.equals(resourceTenantId)) {
            throw new CrossTenantAccessException(principalTenantId, resourceTenantId);
        }
    }

    private Tenant requireExisting(TenantId tenantId) {
        return repository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant: " + tenantId.value()));
    }

    static TenantView toView(Tenant tenant) {
        return new TenantView(
                tenant.id(),
                tenant.name(),
                tenant.status(),
                tenant.timezone(),
                tenant.defaultLanguage(),
                tenant.retentionPolicyDays(),
                tenant.entraTenantId(),
                tenant.createdAt(),
                tenant.updatedAt(),
                tenant.version()
        );
    }
}
