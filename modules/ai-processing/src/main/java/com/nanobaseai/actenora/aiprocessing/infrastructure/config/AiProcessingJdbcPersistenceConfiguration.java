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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.file.Path;

@Configuration
@ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "jdbc")
public class AiProcessingJdbcPersistenceConfiguration {

    @Bean
    @Primary
    AiJobRepository aiJobRepository(DataSource dataSource) {
        return new JdbcAiJobRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    @Primary
    AiAttemptRepository aiAttemptRepository(DataSource dataSource) {
        return new JdbcAiAttemptRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    @Primary
    com.nanobaseai.actenora.aiprocessing.application.port.ProcessingJobDependencyRepository
    processingJobDependencyRepository(DataSource dataSource) {
        return new com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.JdbcProcessingJobDependencyRepository(
                new JdbcTemplate(dataSource));
    }

    @Bean
    @Primary
    com.nanobaseai.actenora.aiprocessing.application.port.ProcessingArtifactRepository
    processingArtifactRepository(
            DataSource dataSource,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            @Value("${actenora.ai.eval-export.enabled:false}") boolean exportEnabled,
            @Value("${actenora.ai.eval-export.root:./var/eval-artifacts}") String exportRoot
    ) {
        com.nanobaseai.actenora.aiprocessing.application.port.ProcessingArtifactExportSink sink = exportEnabled
                ? new com.nanobaseai.actenora.aiprocessing.infrastructure.export.FilesystemProcessingArtifactExportSink(
                        Path.of(exportRoot), objectMapper)
                : com.nanobaseai.actenora.aiprocessing.application.port.ProcessingArtifactExportSink.noop();
        return new com.nanobaseai.actenora.aiprocessing.infrastructure.persistence.JdbcProcessingArtifactRepository(
                new JdbcTemplate(dataSource), sink);
    }

    @Bean
    @Primary
    RoutingDecisionStorePort jdbcRoutingDecisionStore(DataSource dataSource) {
        return new JdbcRoutingDecisionStore(new JdbcTemplate(dataSource));
    }

    @Bean
    @Primary
    AttemptHistoryPort jdbcAttemptHistoryStore(DataSource dataSource) {
        return new JdbcAttemptHistoryStore(new JdbcTemplate(dataSource));
    }

    @Bean
    @Primary
    ShadowExecutionStorePort jdbcShadowExecutionStore(DataSource dataSource) {
        return new JdbcShadowExecutionStore(new JdbcTemplate(dataSource));
    }

    @Bean
    @Primary
    ModelQualityMetricsPort jdbcModelQualityMetricsStore(DataSource dataSource) {
        return new JdbcModelQualityMetricsStore(new JdbcTemplate(dataSource));
    }

    @Bean
    @Primary
    RetryQueuePort jdbcRetryQueue(DataSource dataSource) {
        return new JdbcRetryQueue(new JdbcTemplate(dataSource));
    }
}
