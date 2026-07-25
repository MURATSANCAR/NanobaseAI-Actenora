package com.nanobaseai.actenora.microsoftconnection.infrastructure.graph;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphEgressPolicyTest {

    @Test
    void allowsMicrosoftGraphHosts() {
        GraphEgressPolicy policy = GraphEgressPolicy.defaults();
        assertDoesNotThrow(() -> policy.assertAllowed(URI.create("https://graph.microsoft.com/v1.0/me")));
        assertDoesNotThrow(() -> policy.assertAllowed(URI.create("https://login.microsoftonline.com/common/oauth2/v2.0/token")));
    }

    @Test
    void deniesArbitraryPublicHosts() {
        GraphEgressPolicy policy = GraphEgressPolicy.defaults();
        GraphApiException ex = assertThrows(
                GraphApiException.class,
                () -> policy.assertAllowed(URI.create("https://evil.example/exfiltrate")));
        assertTrue(ex.getMessage().contains("egress denied"));
    }

    @Test
    void deniesHttpEvenForAllowedHost() {
        GraphEgressPolicy policy = new GraphEgressPolicy(Set.of("graph.microsoft.com"));
        assertThrows(
                GraphApiException.class,
                () -> policy.assertAllowed(URI.create("http://graph.microsoft.com/v1.0/me")));
    }
}
