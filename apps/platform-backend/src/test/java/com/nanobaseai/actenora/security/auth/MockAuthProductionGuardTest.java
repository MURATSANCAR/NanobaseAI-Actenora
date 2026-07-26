package com.nanobaseai.actenora.security.auth;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MockAuthProductionGuardTest {

    @Test
    void mockModeAllowedOnLocalProfile() {
        PlatformSecurityConfiguration config = new PlatformSecurityConfiguration();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        assertEquals(AuthMode.MOCK, config.authMode("mock", env));
    }

    @Test
    void mockModeAllowedOnProdFixtureProfile() {
        PlatformSecurityConfiguration config = new PlatformSecurityConfiguration();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod", "prod-fixture");
        assertEquals(AuthMode.MOCK, config.authMode("mock", env));
    }

    @Test
    void mockModeRejectedOnProdProfile() {
        PlatformSecurityConfiguration config = new PlatformSecurityConfiguration();
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class, () -> config.authMode("mock", env));
    }

    @Test
    void entraModeAllowedOnProdProfile() {
        PlatformSecurityConfiguration config = new PlatformSecurityConfiguration();
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("prod");
        assertEquals(AuthMode.ENTRA, config.authMode("entra", env));
    }
}
