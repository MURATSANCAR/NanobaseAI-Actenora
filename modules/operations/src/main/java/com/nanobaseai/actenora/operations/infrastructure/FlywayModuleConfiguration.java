package com.nanobaseai.actenora.operations.infrastructure;

import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Schema-per-context Flyway layout: each bounded context owns
 * {@code classpath:db/migration/<schema>}.
 */
@Configuration
public class FlywayModuleConfiguration {

    public static final String[] MODULE_MIGRATION_LOCATIONS = {
            "classpath:db/migration/identity",
            "classpath:db/migration/tenant",
            "classpath:db/migration/policy",
            "classpath:db/migration/microsoftconnection",
            "classpath:db/migration/meeting",
            "classpath:db/migration/transcript",
            "classpath:db/migration/modelmanagement",
            "classpath:db/migration/aiprocessing",
            "classpath:db/migration/meetingintelligence",
            "classpath:db/migration/approval",
            "classpath:db/migration/template",
            "classpath:db/migration/delivery",
            "classpath:db/migration/audit",
            "classpath:db/migration/operations",
            "classpath:db/migration/notification"
    };

    @Bean
    FlywayConfigurationCustomizer flywayModuleLocations() {
        return configuration -> configuration.locations(MODULE_MIGRATION_LOCATIONS);
    }
}
