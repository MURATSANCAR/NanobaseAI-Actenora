package com.nanobaseai.actenora.meeting.infrastructure.tenancy;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.security.AuthenticatedPrincipal;
import com.nanobaseai.actenora.sharedkernel.security.TenantSecurityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FixedTenantContextTest {

    private static final TenantId TENANT = TenantId.random();
    private static final UUID ACTOR = UUID.randomUUID();

    @AfterEach
    void clearSecurityContext() {
        TenantSecurityContext.clear();
    }

    @Test
    void fallsBackToFixedValuesWhenSecurityContextAbsent() {
        FixedTenantContext context = new FixedTenantContext(TENANT, ACTOR, false);

        assertEquals(TENANT, context.requireTenantId());
        assertEquals(ACTOR, context.requireActorUserId());
    }

    @Test
    void prefersSecurityContextWhenBound() {
        TenantId boundTenant = TenantId.random();
        UUID boundActor = UUID.randomUUID();
        TenantSecurityContext.set(principal(boundTenant, boundActor));

        FixedTenantContext context = new FixedTenantContext(TENANT, ACTOR, false);

        assertEquals(boundTenant, context.requireTenantId());
        assertEquals(boundActor, context.requireActorUserId());
    }

    @Test
    void requireSecurityContextRejectsFallback() {
        FixedTenantContext context = new FixedTenantContext(TENANT, ACTOR, true);

        assertThrows(IllegalStateException.class, context::requireTenantId);
        assertThrows(IllegalStateException.class, context::requireActorUserId);
    }

    @Test
    void requireSecurityContextUsesBoundPrincipal() {
        TenantSecurityContext.set(principal(TENANT, ACTOR));
        FixedTenantContext context = new FixedTenantContext(TenantId.random(), UUID.randomUUID(), true);

        assertEquals(TENANT, context.requireTenantId());
        assertEquals(ACTOR, context.requireActorUserId());
    }

    private static AuthenticatedPrincipal principal(TenantId tenantId, UUID userId) {
        return new AuthenticatedPrincipal(
                tenantId,
                userId,
                "oid-test",
                "test@example.com",
                "Test User",
                Set.of("PARTICIPANT"),
                Set.of("MEETING_READ"),
                false
        );
    }
}
