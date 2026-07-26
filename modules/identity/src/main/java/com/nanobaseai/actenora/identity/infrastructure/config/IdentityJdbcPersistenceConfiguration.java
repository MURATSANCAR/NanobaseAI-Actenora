package com.nanobaseai.actenora.identity.infrastructure.config;

import com.nanobaseai.actenora.identity.application.port.UserRepositoryPort;
import com.nanobaseai.actenora.identity.infrastructure.persistence.JdbcUserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "jdbc")
public class IdentityJdbcPersistenceConfiguration {

    @Bean
    UserRepositoryPort userRepositoryPort(DataSource dataSource) {
        return new JdbcUserRepository(new JdbcTemplate(dataSource));
    }
}
