package com.nanobaseai.actenora.security.auth;

import com.nanobaseai.actenora.identity.application.IdentityApplicationService;
import com.nanobaseai.actenora.identity.infrastructure.persistence.InMemoryUserRepository;
import com.nanobaseai.actenora.identity.infrastructure.provider.EntraIdentityProvider;
import com.nanobaseai.actenora.identity.infrastructure.provider.MockIdentityProvider;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.tenant.application.TenantApplicationService;
import com.nanobaseai.actenora.tenant.api.TenantView;
import com.nanobaseai.actenora.tenant.domain.TenantNotActiveException;
import com.nanobaseai.actenora.tenant.infrastructure.persistence.InMemoryTenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationBindingServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    private TenantApplicationService tenants;
    private IdentityApplicationService identity;
    private TenantView tenantA;
    private TenantView tenantB;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        tenants = new TenantApplicationService(new InMemoryTenantRepository(), clock);
        identity = new IdentityApplicationService(new InMemoryUserRepository(), clock);
        tenantA = tenants.provision("Tenant A", "tid-a", "UTC", "en", 365);
        tenantB = tenants.provision("Tenant B", "tid-b", "UTC", "en", 365);
    }

    @Test
    void mockHeadersBindToMappedTenantNotRequestBody() {
        AuthenticationBindingService binder =
                new AuthenticationBindingService(new MockIdentityProvider(), tenants, identity);

        Optional<AuthenticatedPrincipal> principal = binder.bind(
                Map.of(),
                Map.of(
                        MockIdentityProvider.HEADER_OID, "user-1",
                        MockIdentityProvider.HEADER_TID, "tid-a",
                        MockIdentityProvider.HEADER_EMAIL, "a@example.com",
                        MockIdentityProvider.HEADER_NAME, "User A"
                )
        );

        assertTrue(principal.isPresent());
        assertEquals(tenantA.id(), principal.get().tenantId());
    }

    @Test
    void tenantAUserCannotResolveAgainstTenantBMapping() {
        AuthenticationBindingService binder =
                new AuthenticationBindingService(new MockIdentityProvider(), tenants, identity);
        binder.bind(Map.of(), Map.of(
                MockIdentityProvider.HEADER_OID, "user-1",
                MockIdentityProvider.HEADER_TID, "tid-a",
                MockIdentityProvider.HEADER_EMAIL, "a@example.com",
                MockIdentityProvider.HEADER_NAME, "User A"
        ));

        assertThrows(RuntimeException.class, () -> binder.bind(Map.of(), Map.of(
                MockIdentityProvider.HEADER_OID, "user-1",
                MockIdentityProvider.HEADER_TID, "tid-b",
                MockIdentityProvider.HEADER_EMAIL, "a@example.com",
                MockIdentityProvider.HEADER_NAME, "User A"
        )));
    }

    @Test
    void suspendedTenantIsBlocked() {
        tenants.suspend(tenantA.id(), tenantA.version());
        AuthenticationBindingService binder =
                new AuthenticationBindingService(new MockIdentityProvider(), tenants, identity);

        assertThrows(TenantNotActiveException.class, () -> binder.bind(Map.of(), Map.of(
                MockIdentityProvider.HEADER_OID, "user-2",
                MockIdentityProvider.HEADER_TID, "tid-a",
                MockIdentityProvider.HEADER_EMAIL, "b@example.com",
                MockIdentityProvider.HEADER_NAME, "User B"
        )));
    }

    @Test
    void entraClaimsRequireOidAndTid() {
        AuthenticationBindingService binder =
                new AuthenticationBindingService(new EntraIdentityProvider(), tenants, identity);

        assertTrue(binder.bind(Map.of("oid", "x"), Map.of()).isEmpty());

        Optional<AuthenticatedPrincipal> principal = binder.bind(
                Map.of(
                        "oid", "entra-user",
                        "tid", "tid-b",
                        "email", "b@example.com",
                        "name", "Bob"
                ),
                Map.of()
        );
        assertTrue(principal.isPresent());
        assertEquals(tenantB.id(), principal.get().tenantId());
    }

    @Test
    void missingTenantClaimYieldsEmpty() {
        AuthenticationBindingService binder =
                new AuthenticationBindingService(new MockIdentityProvider(), tenants, identity);
        assertTrue(binder.bind(Map.of(), Map.of(MockIdentityProvider.HEADER_OID, "only-oid")).isEmpty());
    }
}
