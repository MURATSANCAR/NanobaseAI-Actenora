package com.nanobaseai.actenora.policy.infrastructure.config;

import com.nanobaseai.actenora.policy.application.QuotaUsagePort;
import com.nanobaseai.actenora.policy.application.TenantPolicyRepositoryPort;
import com.nanobaseai.actenora.policy.infrastructure.persistence.JdbcQuotaUsageStore;
import com.nanobaseai.actenora.policy.infrastructure.persistence.JdbcTenantPolicyRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "jdbc")
public class PolicyJdbcPersistenceConfiguration {

    @Bean
    TenantPolicyRepositoryPort tenantPolicyRepositoryPort(DataSource dataSource) {
        return new JdbcTenantPolicyRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    QuotaUsagePort quotaUsagePort(DataSource dataSource) {
        return new JdbcQuotaUsageStore(new JdbcTemplate(dataSource));
    }
}
