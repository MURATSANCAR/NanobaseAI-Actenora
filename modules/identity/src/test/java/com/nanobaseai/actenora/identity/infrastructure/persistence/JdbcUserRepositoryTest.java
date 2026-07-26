package com.nanobaseai.actenora.identity.infrastructure.persistence;

import com.nanobaseai.actenora.identity.domain.OptimisticLockException;
import com.nanobaseai.actenora.identity.domain.SystemRole;
import com.nanobaseai.actenora.identity.domain.User;
import com.nanobaseai.actenora.identity.domain.UserStatus;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcUserRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    private static JdbcUserRepository repository;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateSchema() {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        dataSource.setDriverClass(org.h2.Driver.class);
        dataSource.setUrl("jdbc:h2:mem:identity-jdbc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Flyway.configure()
                .dataSource(dataSource)
                .schemas("identity")
                .createSchemas(true)
                .locations("classpath:db/migration/identity")
                .load()
                .migrate();

        jdbc = new JdbcTemplate(dataSource);
        repository = new JdbcUserRepository(jdbc);
    }

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM identity.user_roles");
        jdbc.update("DELETE FROM identity.users");
    }

    @Test
    void saveAndFindByIdLoadsRoles() {
        TenantId tenantId = TenantId.random();
        User user = User.provision(tenantId, "oid-1", "alice@example.com", "Alice", SystemRole.PARTICIPANT, NOW);

        repository.save(user);

        User loaded = repository.findById(user.id()).orElseThrow();
        assertEquals(user.id(), loaded.id());
        assertEquals(EnumSet.of(SystemRole.PARTICIPANT), loaded.roles());
        assertEquals(UserStatus.ACTIVE, loaded.status());
    }

    @Test
    void findByEntraObjectId() {
        TenantId tenantId = TenantId.random();
        User user = User.provision(tenantId, "oid-entra", "bob@example.com", "Bob", SystemRole.APPROVER, NOW);
        repository.save(user);

        User loaded = repository.findByEntraObjectId("oid-entra").orElseThrow();
        assertEquals(user.id(), loaded.id());
        assertTrue(loaded.roles().contains(SystemRole.APPROVER));
    }

    @Test
    void listByTenantIsSortedByEmailAndLoadsRoles() {
        TenantId tenantId = TenantId.random();
        User zebra = User.provision(tenantId, "oid-z", "zebra@example.com", "Z", SystemRole.PARTICIPANT, NOW);
        User alpha = User.provision(tenantId, "oid-a", "alpha@example.com", "A", SystemRole.AUDITOR, NOW);
        repository.save(zebra);
        repository.save(alpha);

        List<User> users = repository.listByTenant(tenantId);
        assertEquals(2, users.size());
        assertEquals("alpha@example.com", users.get(0).email());
        assertEquals(EnumSet.of(SystemRole.AUDITOR), users.get(0).roles());
        assertEquals("zebra@example.com", users.get(1).email());
    }

    @Test
    void saveReplacesRoles() {
        TenantId tenantId = TenantId.random();
        User user = User.provision(tenantId, "oid-roles", "roles@example.com", "Roles", SystemRole.PARTICIPANT, NOW);
        repository.save(user);

        user.grantRole(SystemRole.APPROVER, 0L, NOW);
        repository.save(user);

        User loaded = repository.findById(user.id()).orElseThrow();
        assertTrue(loaded.roles().contains(SystemRole.PARTICIPANT));
        assertTrue(loaded.roles().contains(SystemRole.APPROVER));
    }

    @Test
    void optimisticLockOnConcurrentSave() {
        TenantId tenantId = TenantId.random();
        User user = User.provision(tenantId, "oid-lock", "lock@example.com", "Lock", SystemRole.PARTICIPANT, NOW);
        repository.save(user);

        User stale = repository.findById(user.id()).orElseThrow();
        stale.grantRole(SystemRole.APPROVER, 0L, NOW);
        repository.save(stale);

        User fresh = repository.findById(user.id()).orElseThrow();
        assertThrows(OptimisticLockException.class, () -> {
            fresh.grantRole(SystemRole.AUDITOR, 0L, NOW);
            repository.save(fresh);
        });
    }
}
