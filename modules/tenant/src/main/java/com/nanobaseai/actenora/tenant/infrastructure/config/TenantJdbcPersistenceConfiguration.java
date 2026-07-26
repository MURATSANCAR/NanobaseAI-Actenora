package com.nanobaseai.actenora.tenant.infrastructure.config;

import com.nanobaseai.actenora.tenant.application.port.TenantRepositoryPort;
import com.nanobaseai.actenora.tenant.infrastructure.persistence.JdbcTenantRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "jdbc")
public class TenantJdbcPersistenceConfiguration {

    @Bean
    TenantRepositoryPort tenantRepositoryPort(DataSource dataSource) {
        return new JdbcTenantRepository(new JdbcTemplate(dataSource));
    }
}
