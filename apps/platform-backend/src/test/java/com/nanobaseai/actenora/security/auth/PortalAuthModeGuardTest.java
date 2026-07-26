package com.nanobaseai.actenora.security.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalAuthModeGuardTest {

    @Test
    void msalRequiresEntra() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        PortalAuthModeGuard guard = new PortalAuthModeGuard(env, "msal", "mock");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> guard.run(new DefaultApplicationArguments())
        );
        assertTrue(ex.getMessage().contains("msal requires"));
    }

    @Test
    void msalWithEntraAllowedOnLocal() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        assertDoesNotThrow(() -> new PortalAuthModeGuard(env, "msal", "entra")
                .run(new DefaultApplicationArguments()));
    }

    @Test
    void mockPortalAllowedOnLocal() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        assertDoesNotThrow(() -> new PortalAuthModeGuard(env, "mock", "mock")
                .run(new DefaultApplicationArguments()));
    }

    @Test
    void strictProdRequiresMsalAndEntra() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new PortalAuthModeGuard(env, "mock", "entra")
                        .run(new DefaultApplicationArguments())
        );
        assertTrue(ex.getMessage().contains("must be msal"));
    }

    @Test
    void prodFixtureAllowsMockPortal() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod", "prod-fixture");
        assertDoesNotThrow(() -> new PortalAuthModeGuard(env, "mock", "mock")
                .run(new DefaultApplicationArguments()));
    }

    @Test
    void strictProdAcceptsMsalEntra() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        assertDoesNotThrow(() -> new PortalAuthModeGuard(env, "msal", "entra")
                .run(new DefaultApplicationArguments()));
    }
}
