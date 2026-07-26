package com.nanobaseai.actenora.operations.infrastructure.config;

import com.nanobaseai.actenora.operations.application.port.LegalHoldRepository;
import com.nanobaseai.actenora.operations.infrastructure.persistence.JdbcLegalHoldRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "jdbc")
public class OperationsJdbcPersistenceConfiguration {

    @Bean
    LegalHoldRepository legalHoldRepository(DataSource dataSource) {
        return new JdbcLegalHoldRepository(new JdbcTemplate(dataSource));
    }
}
