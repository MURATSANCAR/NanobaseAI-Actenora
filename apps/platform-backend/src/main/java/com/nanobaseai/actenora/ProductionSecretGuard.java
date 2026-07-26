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

    public ProductionSecretGuard(
            Environment environment,
            @Value("${actenora.allow-default-secrets:false}") boolean allowDefaultSecrets,
            @Value("${spring.datasource.password:}") String datasourcePassword,
            @Value("${spring.rabbitmq.password:}") String rabbitPassword,
            @Value("${actenora.object-storage.secret-key:}") String objectStorageSecret,
            @Value("${actenora.microsoft-graph.webhook.client-state:}") String graphClientState,
            @Value("${actenora.delivery.webhook.secret:}") String deliveryWebhookSecret,
            @Value("${actenora.delivery.portal-link.secret:}") String portalLinkSecret,
            @Value("${spring.mail.host:}") String mailHost
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
    }

    @Override
    public void run(ApplicationArguments args) {
        if (allowDefaultSecrets || !isProductionLike()) {
            return;
        }

        List<String> offenders = new ArrayList<>();
        check("spring.datasource.password", datasourcePassword, offenders);
        check("spring.rabbitmq.password", rabbitPassword, offenders);
        check("actenora.object-storage.secret-key", objectStorageSecret, offenders);
        check("actenora.microsoft-graph.webhook.client-state", graphClientState, offenders);
        check("actenora.delivery.webhook.secret", deliveryWebhookSecret, offenders);
        if (environment.containsProperty("actenora.delivery.portal-link.secret")) {
            check("actenora.delivery.portal-link.secret", portalLinkSecret, offenders);
        }
        checkMailHost(offenders);

        if (!offenders.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start with default/local secrets on a production profile. "
                            + "Offending properties: " + offenders
                            + ". Set strong secrets via environment variables.");
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
