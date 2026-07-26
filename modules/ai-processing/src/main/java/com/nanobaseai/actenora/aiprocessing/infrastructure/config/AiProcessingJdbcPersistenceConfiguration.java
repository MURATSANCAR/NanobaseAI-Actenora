package com.nanobaseai.actenora.aiprocessing.infrastructure.config;

import com.nanobaseai.actenora.aiprocessing.application.port.AiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.AiJobRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.JdbcAiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.JdbcAiJobRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "jdbc")
public class AiProcessingJdbcPersistenceConfiguration {

    @Bean
    AiJobRepository aiJobRepository(DataSource dataSource) {
        return new JdbcAiJobRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    AiAttemptRepository aiAttemptRepository(DataSource dataSource) {
        return new JdbcAiAttemptRepository(new JdbcTemplate(dataSource));
    }
}
