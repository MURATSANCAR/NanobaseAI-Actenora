package com.nanobaseai.actenora.tenant.infrastructure.config;

import com.nanobaseai.actenora.tenant.api.TenantApi;
import com.nanobaseai.actenora.tenant.application.TenantApplicationService;
import com.nanobaseai.actenora.tenant.application.port.TenantRepositoryPort;
import com.nanobaseai.actenora.tenant.infrastructure.persistence.InMemoryTenantRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TenantModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean(TenantRepositoryPort.class)
    TenantRepositoryPort tenantRepositoryPort() {
        return new InMemoryTenantRepository();
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(TenantApi.class)
    TenantApi tenantApi(TenantRepositoryPort tenantRepositoryPort, Clock clock) {
        return new TenantApplicationService(tenantRepositoryPort, clock);
    }
}
