package com.nanobaseai.actenora.aiprocessing.infrastructure.config;

import com.nanobaseai.actenora.aiprocessing.application.port.AiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.AiJobRepository;
import com.nanobaseai.actenora.aiprocessing.application.port.AttemptHistoryPort;
import com.nanobaseai.actenora.aiprocessing.application.port.ModelQualityMetricsPort;
import com.nanobaseai.actenora.aiprocessing.application.port.RetryQueuePort;
import com.nanobaseai.actenora.aiprocessing.application.port.RoutingDecisionStorePort;
import com.nanobaseai.actenora.aiprocessing.application.port.ShadowExecutionStorePort;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.JdbcAiAttemptRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.JdbcAiJobRepository;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.JdbcAttemptHistoryStore;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.JdbcModelQualityMetricsStore;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.JdbcRetryQueue;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.JdbcRoutingDecisionStore;
import com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.JdbcShadowExecutionStore;
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

    @Bean
    RoutingDecisionStorePort jdbcRoutingDecisionStore(DataSource dataSource) {
        return new JdbcRoutingDecisionStore(new JdbcTemplate(dataSource));
    }

    @Bean
    AttemptHistoryPort jdbcAttemptHistoryStore(DataSource dataSource) {
        return new JdbcAttemptHistoryStore(new JdbcTemplate(dataSource));
    }

    @Bean
    ShadowExecutionStorePort jdbcShadowExecutionStore(DataSource dataSource) {
        return new JdbcShadowExecutionStore(new JdbcTemplate(dataSource));
    }

    @Bean
    ModelQualityMetricsPort jdbcModelQualityMetricsStore(DataSource dataSource) {
        return new JdbcModelQualityMetricsStore(new JdbcTemplate(dataSource));
    }

    @Bean
    RetryQueuePort jdbcRetryQueue(DataSource dataSource) {
        return new JdbcRetryQueue(new JdbcTemplate(dataSource));
    }
}
