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

/**
 * Blocks production boot when local default secrets are still in use (FAZ 2).
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

    private final Environment environment;
    private final boolean allowDefaultSecrets;
    private final String datasourcePassword;
    private final String rabbitPassword;
    private final String objectStorageSecret;

    public ProductionSecretGuard(
            Environment environment,
            @Value("${actenora.allow-default-secrets:false}") boolean allowDefaultSecrets,
            @Value("${spring.datasource.password:}") String datasourcePassword,
            @Value("${spring.rabbitmq.password:}") String rabbitPassword,
            @Value("${actenora.object-storage.secret-key:}") String objectStorageSecret) {
        this.environment = environment;
        this.allowDefaultSecrets = allowDefaultSecrets;
        this.datasourcePassword = datasourcePassword;
        this.rabbitPassword = rabbitPassword;
        this.objectStorageSecret = objectStorageSecret;
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
        for (String fragment : FORBIDDEN_FRAGMENTS) {
            if (lower.contains(fragment)) {
                offenders.add(name);
                return;
            }
        }
    }
}
