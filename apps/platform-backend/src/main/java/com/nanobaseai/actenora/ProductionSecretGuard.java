package com.nanobaseai.actenora;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Blocks production boot when local default secrets are still in use (FAZ 2, Wave 4).
 */
@Component
public class ProductionSecretGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionSecretGuard.class);

    private static final List<String> FORBIDDEN_FRAGMENTS = List.of(
            "_change_me",
            "actenora_local",
            "actenora_minio",
            "changeme",
            "changeit"
    );

    private static final Set<String> FORBIDDEN_EXACT_VALUES = Set.of(
            "local-graph-client-state",
            "local-delivery-webhook-secret",
            "actenora-local-portal-secret"
    );

    private static final Set<String> FORBIDDEN_MAIL_HOSTS = Set.of(
            "localhost",
            "127.0.0.1",
            "mailhog",
            "::1"
    );

    private final Environment environment;
    private final boolean allowDefaultSecrets;
    private final String datasourcePassword;
    private final String rabbitPassword;
    private final String objectStorageSecret;
    private final String graphClientState;
    private final String deliveryWebhookSecret;
    private final String portalLinkSecret;
    private final String mailHost;
    private final boolean microsoftGraphEnabled;
    private final String persistenceMode;
    private final String messagingMode;
    private final String graphAuthMode;
    private final String graphCertificatePath;
    private final String graphPrivateKeyPath;
    private final String knowledgeEmbeddingMode;
    private final String knowledgeEmbeddingBaseUrl;

    public ProductionSecretGuard(
            Environment environment,
            @Value("${actenora.allow-default-secrets:false}") boolean allowDefaultSecrets,
            @Value("${spring.datasource.password:}") String datasourcePassword,
            @Value("${spring.rabbitmq.password:}") String rabbitPassword,
            @Value("${actenora.object-storage.secret-key:}") String objectStorageSecret,
            @Value("${actenora.microsoft-graph.webhook.client-state:}") String graphClientState,
            @Value("${actenora.delivery.webhook.secret:}") String deliveryWebhookSecret,
            @Value("${actenora.delivery.portal-link.secret:}") String portalLinkSecret,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${actenora.microsoft-graph.enabled:false}") boolean microsoftGraphEnabled,
            @Value("${actenora.persistence.mode:inmemory}") String persistenceMode,
            @Value("${actenora.messaging.mode:inmemory}") String messagingMode,
            @Value("${actenora.microsoft-graph.auth-mode:CLIENT_SECRET}") String graphAuthMode,
            @Value("${actenora.microsoft-graph.certificate-pem-path:}") String graphCertificatePath,
            @Value("${actenora.microsoft-graph.private-key-pem-path:}") String graphPrivateKeyPath,
            @Value("${actenora.knowledge.embedding.mode:hash}") String knowledgeEmbeddingMode,
            @Value("${actenora.knowledge.embedding.base-url:}") String knowledgeEmbeddingBaseUrl
    ) {
        this.environment = environment;
        this.allowDefaultSecrets = allowDefaultSecrets;
        this.datasourcePassword = datasourcePassword;
        this.rabbitPassword = rabbitPassword;
        this.objectStorageSecret = objectStorageSecret;
        this.graphClientState = graphClientState;
        this.deliveryWebhookSecret = deliveryWebhookSecret;
        this.portalLinkSecret = portalLinkSecret;
        this.mailHost = mailHost;
        this.microsoftGraphEnabled = microsoftGraphEnabled;
        this.persistenceMode = persistenceMode;
        this.messagingMode = messagingMode;
        this.graphAuthMode = graphAuthMode;
        this.graphCertificatePath = graphCertificatePath;
        this.graphPrivateKeyPath = graphPrivateKeyPath;
        this.knowledgeEmbeddingMode = knowledgeEmbeddingMode;
        this.knowledgeEmbeddingBaseUrl = knowledgeEmbeddingBaseUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProductionLike()) {
            return;
        }

        List<String> offenders = new ArrayList<>();
        if (!allowDefaultSecrets) {
            check("spring.datasource.password", datasourcePassword, offenders);
            check("spring.rabbitmq.password", rabbitPassword, offenders);
            check("actenora.object-storage.secret-key", objectStorageSecret, offenders);
            check("actenora.microsoft-graph.webhook.client-state", graphClientState, offenders);
            check("actenora.delivery.webhook.secret", deliveryWebhookSecret, offenders);
            check("actenora.delivery.portal-link.secret", portalLinkSecret, offenders);
            checkMailHost(offenders);
        }
        if (microsoftGraphEnabled && !"jdbc".equalsIgnoreCase(persistenceMode)) {
            offenders.add("actenora.persistence.mode (jdbc required when Microsoft Graph is enabled)");
        }
        if (microsoftGraphEnabled && !"jdbc-rabbit".equalsIgnoreCase(messagingMode)) {
            offenders.add("actenora.messaging.mode (jdbc-rabbit required when Microsoft Graph is enabled)");
        }
        if (microsoftGraphEnabled && !"CERTIFICATE".equalsIgnoreCase(graphAuthMode)) {
            offenders.add("actenora.microsoft-graph.auth-mode (CERTIFICATE required in production)");
        }
        if (microsoftGraphEnabled && (graphCertificatePath == null || graphCertificatePath.isBlank())) {
            offenders.add("actenora.microsoft-graph.certificate-pem-path (required in production)");
        }
        if (microsoftGraphEnabled && (graphPrivateKeyPath == null || graphPrivateKeyPath.isBlank())) {
            offenders.add("actenora.microsoft-graph.private-key-pem-path (required in production)");
        }
        if ("hash".equalsIgnoreCase(knowledgeEmbeddingMode)) {
            offenders.add("actenora.knowledge.embedding.mode (hash forbidden in production)");
        }
        if ("openai-compatible".equalsIgnoreCase(knowledgeEmbeddingMode)
                && (knowledgeEmbeddingBaseUrl == null || knowledgeEmbeddingBaseUrl.isBlank())) {
            offenders.add("actenora.knowledge.embedding.base-url (required when mode=openai-compatible)");
        }

        if (!offenders.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start with unsafe production configuration. "
                            + "Offending properties: " + offenders
                            + ". Set durable modes, certificate authentication, and strong secrets via environment variables.");
        }
        log.info("Production secret guard passed");
    }

    private boolean isProductionLike() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(p -> p.toLowerCase(Locale.ROOT))
                .anyMatch(p -> p.equals("prod") || p.equals("production"));
    }

    private static void check(String name, String value, List<String> offenders) {
        if (value == null || value.isBlank()) {
            offenders.add(name + " (empty)");
            return;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_EXACT_VALUES.contains(lower)) {
            offenders.add(name + " (local default)");
            return;
        }
        for (String fragment : FORBIDDEN_FRAGMENTS) {
            if (lower.contains(fragment)) {
                offenders.add(name);
                return;
            }
        }
    }

    private void checkMailHost(List<String> offenders) {
        if (ActenoraProfiles.isProdFixtureProfile(environment)) {
            return;
        }
        if (mailHost == null || mailHost.isBlank()) {
            return;
        }
        String lower = mailHost.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_MAIL_HOSTS.contains(lower) || lower.contains("mailhog")) {
            offenders.add("spring.mail.host (local/mailhog forbidden on prod)");
        }
    }
}
