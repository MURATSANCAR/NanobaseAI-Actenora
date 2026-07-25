package com.nanobaseai.actenora.policy.infrastructure.config;

import com.nanobaseai.actenora.policy.api.PolicyApi;
import com.nanobaseai.actenora.policy.application.PolicyCachePort;
import com.nanobaseai.actenora.policy.application.PolicyEvaluationService;
import com.nanobaseai.actenora.policy.application.QuotaUsagePort;
import com.nanobaseai.actenora.policy.application.TenantPolicyRepositoryPort;
import com.nanobaseai.actenora.policy.infrastructure.cache.InMemoryPolicyCache;
import com.nanobaseai.actenora.policy.infrastructure.persistence.InMemoryQuotaUsageStore;
import com.nanobaseai.actenora.policy.infrastructure.persistence.InMemoryTenantPolicyRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class PolicyModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean(TenantPolicyRepositoryPort.class)
    TenantPolicyRepositoryPort tenantPolicyRepositoryPort() {
        return new InMemoryTenantPolicyRepository();
    }

    @Bean
    @ConditionalOnMissingBean(PolicyCachePort.class)
    PolicyCachePort policyCachePort() {
        return new InMemoryPolicyCache();
    }

    @Bean
    @ConditionalOnMissingBean(QuotaUsagePort.class)
    QuotaUsagePort quotaUsagePort() {
        return new InMemoryQuotaUsageStore();
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock policyClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(PolicyApi.class)
    PolicyApi policyApi(
            TenantPolicyRepositoryPort repository,
            PolicyCachePort cache,
            QuotaUsagePort quotaUsage,
            Clock clock
    ) {
        return new PolicyEvaluationService(repository, cache, quotaUsage, clock);
    }
}
