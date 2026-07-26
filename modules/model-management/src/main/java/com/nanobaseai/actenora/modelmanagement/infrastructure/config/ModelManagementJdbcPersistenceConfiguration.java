package com.nanobaseai.actenora.modelmanagement.infrastructure.config;

import com.nanobaseai.actenora.modelmanagement.application.ModelDefinitionRepository;
import com.nanobaseai.actenora.modelmanagement.application.ModelDeploymentRepository;
import com.nanobaseai.actenora.modelmanagement.infrastructure.persistence.JdbcModelDefinitionRepository;
import com.nanobaseai.actenora.modelmanagement.infrastructure.persistence.JdbcModelDeploymentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "jdbc")
public class ModelManagementJdbcPersistenceConfiguration {

    @Bean
    ModelDefinitionRepository modelDefinitionRepository(DataSource dataSource) {
        return new JdbcModelDefinitionRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    ModelDeploymentRepository modelDeploymentRepository(DataSource dataSource) {
        return new JdbcModelDeploymentRepository(new JdbcTemplate(dataSource));
    }
}
