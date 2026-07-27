package com.nanobaseai.actenora;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionSecretGuardTest {

    private static ProductionSecretGuard guard(
            MockEnvironment env,
            boolean allowDefaults,
            String db,
            String rabbit,
            String minio,
            String graphState,
            String deliveryWebhook,
            String portalLink,
            String mailHost,
            boolean graphEnabled,
            String persistence,
            String messaging,
            String graphAuth,
            String cert,
            String key,
            String embeddingMode,
            String embeddingBaseUrl,
            String embeddingModelId,
            boolean redisCoord,
            String redisPassword
    ) {
        return new ProductionSecretGuard(
                env,
                allowDefaults,
                db,
                rabbit,
                minio,
                graphState,
                deliveryWebhook,
                portalLink,
                mailHost,
                graphEnabled,
                persistence,
                messaging,
                graphAuth,
                cert,
                key,
                embeddingMode,
                embeddingBaseUrl,
                embeddingModelId,
                redisCoord,
                redisPassword
        );
    }

    @Test
    void prodProfileRejectsLocalDefaults() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSecretGuard g = guard(
                env, false,
                "strong-db-pass", "strong-rabbit-pass", "strong-minio-key",
                "local-graph-client-state", "local-delivery-webhook-secret", "actenora-local-portal-secret",
                "localhost", false, "jdbc", "jdbc-rabbit", "CLIENT_SECRET", "", "",
                "openai-compatible", "http://embeddings.internal", "nomic-embed", false, ""
        );
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> g.run(new DefaultApplicationArguments()));
        assertTrue(ex.getMessage().contains("actenora.microsoft-graph.webhook.client-state"));
    }

    @Test
    void prodProfileAcceptsStrongSecrets() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSecretGuard g = guard(
                env, false,
                "prod-db-secret-xyz", "prod-rabbit-secret-xyz", "prod-minio-secret-xyz",
                "prod-graph-client-state-secret", "prod-delivery-webhook-secret", "prod-portal-link-hmac-secret",
                "smtp.office365.com", true, "jdbc", "jdbc-rabbit", "CERTIFICATE",
                "/run/secrets/graph.crt", "/run/secrets/graph.key",
                "openai-compatible", "http://embeddings.internal", "nomic-embed", true, "prod-redis-secret-xyz"
        );
        assertDoesNotThrow(() -> g.run(new DefaultApplicationArguments()));
    }

    @Test
    void prodFixtureProfileAllowsMailhogHostWithStrongSecrets() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod", "prod-fixture");
        ProductionSecretGuard g = guard(
                env, false,
                "prod-db-secret-xyz", "prod-rabbit-secret-xyz", "prod-minio-secret-xyz",
                "prod-graph-client-state-secret", "prod-delivery-webhook-secret", "prod-portal-link-hmac-secret",
                "mailhog", false, "jdbc", "jdbc-rabbit", "CLIENT_SECRET", "", "",
                "openai-compatible", "http://embeddings.internal", "nomic-embed", false, ""
        );
        assertDoesNotThrow(() -> g.run(new DefaultApplicationArguments()));
    }

    @Test
    void localProfileAllowsDefaults() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        ProductionSecretGuard g = guard(
                env, false,
                "actenora_local", "changeme", "actenora_minio",
                "local-graph-client-state", "local-delivery-webhook-secret", "actenora-local-portal-secret",
                "localhost", true, "inmemory", "inmemory", "CLIENT_SECRET", "", "",
                "hash", "", "", false, ""
        );
        assertDoesNotThrow(() -> g.run(new DefaultApplicationArguments()));
    }

    @Test
    void prodGraphRequiresDurablePersistenceAndMessaging() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSecretGuard g = guard(
                env, false,
                "prod-db-secret-xyz", "prod-rabbit-secret-xyz", "prod-minio-secret-xyz",
                "prod-graph-client-state-secret", "prod-delivery-webhook-secret", "prod-portal-link-hmac-secret",
                "smtp.office365.com", true, "inmemory", "inmemory", "CLIENT_SECRET", "", "",
                "openai-compatible", "http://embeddings.internal", "nomic-embed", false, ""
        );
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> g.run(new DefaultApplicationArguments()));
        assertTrue(ex.getMessage().contains("actenora.persistence.mode"));
    }

    @Test
    void prodGraphRequiresCertificateAuth() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSecretGuard g = guard(
                env, false,
                "prod-db-secret-xyz", "prod-rabbit-secret-xyz", "prod-minio-secret-xyz",
                "prod-graph-client-state-secret", "prod-delivery-webhook-secret", "prod-portal-link-hmac-secret",
                "smtp.office365.com", true, "jdbc", "jdbc-rabbit", "CLIENT_SECRET", "", "",
                "openai-compatible", "http://embeddings.internal", "nomic-embed", false, ""
        );
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> g.run(new DefaultApplicationArguments()));
        assertTrue(ex.getMessage().contains("actenora.microsoft-graph.auth-mode"));
    }

    @Test
    void prodRejectsHashEmbeddings() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSecretGuard g = guard(
                env, false,
                "prod-db-secret-xyz", "prod-rabbit-secret-xyz", "prod-minio-secret-xyz",
                "prod-graph-client-state-secret", "prod-delivery-webhook-secret", "prod-portal-link-hmac-secret",
                "smtp.office365.com", false, "jdbc", "jdbc-rabbit", "CLIENT_SECRET", "", "",
                "hash", "", "", false, ""
        );
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> g.run(new DefaultApplicationArguments()));
        assertTrue(ex.getMessage().contains("actenora.knowledge.embedding.mode"));
    }

    @Test
    void prodRequiresRedisPasswordWhenCoordinationEnabled() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSecretGuard g = guard(
                env, false,
                "prod-db-secret-xyz", "prod-rabbit-secret-xyz", "prod-minio-secret-xyz",
                "prod-graph-client-state-secret", "prod-delivery-webhook-secret", "prod-portal-link-hmac-secret",
                "smtp.office365.com", false, "jdbc", "jdbc-rabbit", "CLIENT_SECRET", "", "",
                "openai-compatible", "http://embeddings.internal", "nomic-embed", true, ""
        );
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> g.run(new DefaultApplicationArguments()));
        assertTrue(ex.getMessage().contains("spring.data.redis.password"));
    }
}
