package com.nanobaseai.actenora.security.auth;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockAuthProductionGuardTest {

    @Test
    void headersModeAllowedOnLocalProfile() {
        PlatformSecurityConfiguration config = new PlatformSecurityConfiguration();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        assertEquals(AuthMode.HEADERS, config.authMode("headers", env));
    }

    @Test
    void legacyMockAliasMapsToHeaders() {
        PlatformSecurityConfiguration config = new PlatformSecurityConfiguration();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        assertEquals(AuthMode.HEADERS, config.authMode("mock", env));
    }

    @Test
    void headersModeAllowedOnProdFixtureProfile() {
        PlatformSecurityConfiguration config = new PlatformSecurityConfiguration();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod", "prod-fixture");
        assertEquals(AuthMode.HEADERS, config.authMode("headers", env));
    }

    @Test
    void headersModeRejectedOnProdProfile() {
        PlatformSecurityConfiguration config = new PlatformSecurityConfiguration();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class, () -> config.authMode("headers", env));
    }

    @Test
    void entraModeAllowedOnProdProfile() {
        PlatformSecurityConfiguration config = new PlatformSecurityConfiguration();
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("prod");
        assertEquals(AuthMode.ENTRA, config.authMode("entra", env));
    }
}
