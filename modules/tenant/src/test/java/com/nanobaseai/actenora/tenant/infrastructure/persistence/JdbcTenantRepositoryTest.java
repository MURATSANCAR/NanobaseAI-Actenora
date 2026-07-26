package com.nanobaseai.actenora.tenant.infrastructure.persistence;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.tenant.domain.OptimisticLockException;
import com.nanobaseai.actenora.tenant.domain.Tenant;
import com.nanobaseai.actenora.tenant.domain.TenantMembership;
import com.nanobaseai.actenora.tenant.domain.TenantStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcTenantRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    private static JdbcTenantRepository repository;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateSchema() {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        dataSource.setDriverClass(org.h2.Driver.class);
        dataSource.setUrl("jdbc:h2:mem:tenant-jdbc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS tenant");
        jdbc.execute("""
            CREATE TABLE tenant.tenants (
                id UUID PRIMARY KEY,
                name TEXT NOT NULL,
                status TEXT NOT NULL,
                timezone TEXT NOT NULL,
                default_language TEXT NOT NULL,
                retention_policy_days INTEGER NOT NULL,
                entra_tenant_id TEXT NOT NULL,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                version BIGINT NOT NULL DEFAULT 0
            )
            """);
        jdbc.execute("""
            CREATE TABLE tenant.tenant_memberships (
                tenant_id UUID NOT NULL,
                user_id UUID NOT NULL,
                created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                PRIMARY KEY (tenant_id, user_id)
            )
            """);
        repository = new JdbcTenantRepository(jdbc);
    }

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM tenant.tenant_memberships");
        jdbc.update("DELETE FROM tenant.tenants");
    }

    @Test
    void saveAndFindById() {
        Tenant tenant = Tenant.provision("Acme", "entra-tid-1", "UTC", "en", 365, NOW);
        repository.save(tenant);

        Tenant loaded = repository.findById(tenant.id()).orElseThrow();
        assertEquals("Acme", loaded.name());
        assertEquals(TenantStatus.ACTIVE, loaded.status());
        assertEquals("entra-tid-1", loaded.entraTenantId());
    }

    @Test
    void findByEntraTenantId() {
        Tenant tenant = Tenant.provision("Beta", "entra-tid-2", "Europe/Istanbul", "tr", 730, NOW);
        repository.save(tenant);

        Tenant loaded = repository.findByEntraTenantId("entra-tid-2").orElseThrow();
        assertEquals(tenant.id(), loaded.id());
    }

    @Test
    void membershipIsTracked() {
        Tenant tenant = Tenant.provision("Gamma", "entra-tid-3", "UTC", "en", 365, NOW);
        repository.save(tenant);
        UUID userId = UUID.randomUUID();

        assertFalse(repository.isMember(tenant.id(), userId));
        repository.saveMembership(new TenantMembership(tenant.id(), userId));
        assertTrue(repository.isMember(tenant.id(), userId));
    }

    @Test
    void saveMembershipIsIdempotent() {
        Tenant tenant = Tenant.provision("Delta", "entra-tid-4", "UTC", "en", 365, NOW);
        repository.save(tenant);
        UUID userId = UUID.randomUUID();
        TenantMembership membership = new TenantMembership(tenant.id(), userId);

        repository.saveMembership(membership);
        repository.saveMembership(membership);

        assertTrue(repository.isMember(tenant.id(), userId));
    }

    @Test
    void optimisticLockOnUpdate() {
        Tenant tenant = Tenant.provision("Epsilon", "entra-tid-5", "UTC", "en", 365, NOW);
        repository.save(tenant);

        Tenant stale = repository.findById(tenant.id()).orElseThrow();
        stale.suspend(0L, NOW);
        repository.save(stale);

        Tenant fresh = repository.findById(tenant.id()).orElseThrow();
        assertThrows(OptimisticLockException.class, () -> {
            fresh.activate(0L, NOW);
            repository.save(fresh);
        });
    }
}
