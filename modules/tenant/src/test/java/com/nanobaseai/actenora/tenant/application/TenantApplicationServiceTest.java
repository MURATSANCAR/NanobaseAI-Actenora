package com.nanobaseai.actenora.tenant.application;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.tenant.api.TenantView;
import com.nanobaseai.actenora.tenant.domain.CrossTenantAccessException;
import com.nanobaseai.actenora.tenant.domain.OptimisticLockException;
import com.nanobaseai.actenora.tenant.domain.TenantNotActiveException;
import com.nanobaseai.actenora.tenant.domain.TenantStatus;
import com.nanobaseai.actenora.tenant.infrastructure.persistence.InMemoryTenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    private TenantApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TenantApplicationService(
                new InMemoryTenantRepository(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void provisionAndResolveByEntraTenantId() {
        TenantView tenant = service.provision("Acme", "entra-tid-1", "Europe/Istanbul", "tr", 730);
        assertEquals(TenantStatus.ACTIVE, tenant.status());
        assertTrue(service.findByEntraTenantId("entra-tid-1").isPresent());
    }

    @Test
    void duplicateEntraTenantMappingRejected() {
        service.provision("Acme", "entra-tid-dup", "UTC", "en", 365);
        assertThrows(
                DuplicateEntraTenantException.class,
                () -> service.provision("Other", "entra-tid-dup", "UTC", "en", 365)
        );
    }

    @Test
    void suspendedTenantCannotBeRequiredActive() {
        TenantView tenant = service.provision("Acme", "entra-tid-2", "UTC", "en", 365);
        service.suspend(tenant.id(), tenant.version());
        assertThrows(TenantNotActiveException.class, () -> service.requireActive(tenant.id()));
    }

    @Test
    void optimisticLockOnSuspend() {
        TenantView tenant = service.provision("Acme", "entra-tid-3", "UTC", "en", 365);
        assertThrows(OptimisticLockException.class, () -> service.suspend(tenant.id(), 99L));
    }

    @Test
    void crossTenantAccessDenied() {
        TenantId a = TenantId.random();
        TenantId b = TenantId.random();
        assertThrows(CrossTenantAccessException.class, () -> service.assertSameTenant(a, b));
    }

    @Test
    void membershipIsTracked() {
        TenantView tenant = service.provision("Acme", "entra-tid-4", "UTC", "en", 365);
        UUID userId = UUID.randomUUID();
        service.ensureMembership(tenant.id(), userId);
        assertTrue(service.isMember(tenant.id(), userId));
    }
}
