package com.nanobaseai.actenora.identity.application;

import com.nanobaseai.actenora.identity.domain.DuplicateEntraMappingException;
import com.nanobaseai.actenora.identity.domain.OptimisticLockException;
import com.nanobaseai.actenora.identity.domain.Permission;
import com.nanobaseai.actenora.identity.domain.SystemRole;
import com.nanobaseai.actenora.identity.infrastructure.persistence.InMemoryUserRepository;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.IdentityClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    private InMemoryUserRepository users;
    private IdentityApplicationService service;
    private TenantId tenantA;
    private TenantId tenantB;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        service = new IdentityApplicationService(users, Clock.fixed(NOW, ZoneOffset.UTC));
        tenantA = TenantId.random();
        tenantB = TenantId.random();
    }

    @Test
    void resolvePrincipalProvisionsParticipant() {
        AuthenticatedPrincipal principal = service.resolvePrincipal(
                tenantA,
                new IdentityClaims("oid-1", "tid-a", "a@example.com", "Alice", false)
        );

        assertEquals(tenantA, principal.tenantId());
        assertTrue(principal.hasRole(SystemRole.PARTICIPANT.code()));
        assertTrue(principal.hasPermission(Permission.MEETING_READ.code()));
    }

    @Test
    void duplicateEntraAcrossTenantsIsRejected() {
        service.resolvePrincipal(
                tenantA,
                new IdentityClaims("oid-shared", "tid-a", "shared@example.com", "Shared", false)
        );

        assertThrows(
                DuplicateEntraMappingException.class,
                () -> service.resolvePrincipal(
                        tenantB,
                        new IdentityClaims("oid-shared", "tid-b", "shared@example.com", "Shared", false)
                )
        );
    }

    @Test
    void grantRoleUsesOptimisticLocking() {
        AuthenticatedPrincipal principal = service.resolvePrincipal(
                tenantA,
                new IdentityClaims("oid-2", "tid-a", "b@example.com", "Bob", false)
        );

        service.grantRole(tenantA, principal.userId(), SystemRole.APPROVER, 0L, principal.userId());

        assertThrows(
                OptimisticLockException.class,
                () -> service.grantRole(tenantA, principal.userId(), SystemRole.AUDITOR, 0L, principal.userId())
        );
    }

    @Test
    void listUsersIsTenantScoped() {
        service.resolvePrincipal(tenantA, new IdentityClaims("oid-a", "tid-a", "a@x.com", "A", false));
        service.resolvePrincipal(tenantB, new IdentityClaims("oid-b", "tid-b", "b@x.com", "B", false));

        assertEquals(1, service.listUsers(tenantA).size());
        assertEquals(1, service.listUsers(tenantB).size());
        assertTrue(service.findById(tenantA, service.listUsers(tenantB).getFirst().id()).isEmpty());
    }

    @Test
    void missingTenantClaimCannotBeInventedFromUserIdGuess() {
        AuthenticatedPrincipal principal = service.resolvePrincipal(
                tenantA,
                new IdentityClaims("oid-3", "tid-a", "c@example.com", "C", false)
        );
        UUID foreignUser = UUID.randomUUID();
        assertTrue(service.findById(tenantB, principal.userId()).isEmpty());
        assertTrue(service.findById(tenantA, foreignUser).isEmpty());
    }
}
