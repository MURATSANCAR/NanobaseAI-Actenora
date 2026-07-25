package com.nanobaseai.actenora.identity.infrastructure.config;

import com.nanobaseai.actenora.identity.api.IdentityApi;
import com.nanobaseai.actenora.identity.application.IdentityApplicationService;
import com.nanobaseai.actenora.identity.application.port.UserRepositoryPort;
import com.nanobaseai.actenora.identity.infrastructure.persistence.InMemoryUserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class IdentityModuleConfiguration {

    @Bean
    @ConditionalOnMissingBean(UserRepositoryPort.class)
    UserRepositoryPort userRepositoryPort() {
        return new InMemoryUserRepository();
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock identityClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(IdentityApi.class)
    IdentityApi identityApi(UserRepositoryPort userRepositoryPort, Clock clock) {
        return new IdentityApplicationService(userRepositoryPort, clock);
    }
}
