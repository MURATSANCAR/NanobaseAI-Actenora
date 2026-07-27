package com.nanobaseai.actenora;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionSecretGuardTest {

    @Test
    void prodProfileRejectsLocalDefaults() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        ProductionSecretGuard guard = new ProductionSecretGuard(
                env,
                false,
                "strong-db-pass",
                "strong-rabbit-pass",
                "strong-minio-key",
                "local-graph-client-state",
                "local-delivery-webhook-secret",
                "actenora-local-portal-secret",
                "localhost",
                false,
                "jdbc",
                "jdbc-rabbit",
                "CLIENT_SECRET",
                "",
                "",
                "openai-compatible",
                "http://embeddings.internal"
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> guard.run(new DefaultApplicationArguments())
        );
        assertTrue(ex.getMessage().contains("actenora.microsoft-graph.webhook.client-state"));
        assertTrue(ex.getMessage().contains("actenora.delivery.webhook.secret"));
        assertTrue(ex.getMessage().contains("actenora.delivery.portal-link.secret"));
        assertTrue(ex.getMessage().contains("spring.mail.host"));
    }

    @Test
    void prodProfileAcceptsStrongSecrets() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        ProductionSecretGuard guard = new ProductionSecretGuard(
                env,
                false,
                "prod-db-secret-xyz",
                "prod-rabbit-secret-xyz",
                "prod-minio-secret-xyz",
                "prod-graph-client-state-secret",
                "prod-delivery-webhook-secret",
                "prod-portal-link-hmac-secret",
                "smtp.office365.com",
                true,
                "jdbc",
                "jdbc-rabbit",
                "CERTIFICATE",
                "/run/secrets/graph.crt",
                "/run/secrets/graph.key",
                "openai-compatible",
                "http://embeddings.internal"
        );

        assertDoesNotThrow(() -> guard.run(new DefaultApplicationArguments()));
    }

    @Test
    void prodFixtureProfileAllowsMailhogHostWithStrongSecrets() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod", "prod-fixture");

        ProductionSecretGuard guard = new ProductionSecretGuard(
                env,
                false,
                "prod-db-secret-xyz",
                "prod-rabbit-secret-xyz",
                "prod-minio-secret-xyz",
                "prod-graph-client-state-secret",
                "prod-delivery-webhook-secret",
                "prod-portal-link-hmac-secret",
                "mailhog",
                false,
                "jdbc",
                "jdbc-rabbit",
                "CLIENT_SECRET",
                "",
                "",
                "openai-compatible",
                "http://embeddings.internal"
        );

        assertDoesNotThrow(() -> guard.run(new DefaultApplicationArguments()));
    }

    @Test
    void localProfileAllowsDefaults() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");

        ProductionSecretGuard guard = new ProductionSecretGuard(
                env,
                false,
                "actenora_local",
                "changeme",
                "actenora_minio",
                "local-graph-client-state",
                "local-delivery-webhook-secret",
                "actenora-local-portal-secret",
                "localhost",
                true,
                "inmemory",
                "inmemory",
                "CLIENT_SECRET",
                "",
                "",
                "hash",
                ""
        );

        assertDoesNotThrow(() -> guard.run(new DefaultApplicationArguments()));
    }

    @Test
    void prodGraphRequiresDurablePersistenceAndMessaging() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSecretGuard guard = new ProductionSecretGuard(
                env,
                false,
                "prod-db-secret-xyz",
                "prod-rabbit-secret-xyz",
                "prod-minio-secret-xyz",
                "prod-graph-client-state-secret",
                "prod-delivery-webhook-secret",
                "prod-portal-link-hmac-secret",
                "smtp.office365.com",
                true,
                "inmemory",
                "inmemory",
                "CLIENT_SECRET",
                "",
                "",
                "openai-compatible",
                "http://embeddings.internal"
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> guard.run(new DefaultApplicationArguments()));
        assertTrue(ex.getMessage().contains("actenora.persistence.mode"));
        assertTrue(ex.getMessage().contains("actenora.messaging.mode"));
    }

    @Test
    void prodGraphRequiresCertificateAuth() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSecretGuard guard = new ProductionSecretGuard(
                env,
                false,
                "prod-db-secret-xyz",
                "prod-rabbit-secret-xyz",
                "prod-minio-secret-xyz",
                "prod-graph-client-state-secret",
                "prod-delivery-webhook-secret",
                "prod-portal-link-hmac-secret",
                "smtp.office365.com",
                true,
                "jdbc",
                "jdbc-rabbit",
                "CLIENT_SECRET",
                "",
                "",
                "openai-compatible",
                "http://embeddings.internal"
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> guard.run(new DefaultApplicationArguments()));
        assertTrue(ex.getMessage().contains("actenora.microsoft-graph.auth-mode"));
        assertTrue(ex.getMessage().contains("certificate-pem-path"));
        assertTrue(ex.getMessage().contains("private-key-pem-path"));
    }

    @Test
    void prodRejectsHashEmbeddings() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSecretGuard guard = new ProductionSecretGuard(
                env,
                false,
                "prod-db-secret-xyz",
                "prod-rabbit-secret-xyz",
                "prod-minio-secret-xyz",
                "prod-graph-client-state-secret",
                "prod-delivery-webhook-secret",
                "prod-portal-link-hmac-secret",
                "smtp.office365.com",
                false,
                "jdbc",
                "jdbc-rabbit",
                "CLIENT_SECRET",
                "",
                "",
                "hash",
                ""
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> guard.run(new DefaultApplicationArguments()));
        assertTrue(ex.getMessage().contains("actenora.knowledge.embedding.mode"));
    }
}
