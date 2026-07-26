package com.nanobaseai.actenora.audit.infrastructure.config;

import com.nanobaseai.actenora.audit.application.port.AuditEntryStore;
import com.nanobaseai.actenora.audit.infrastructure.persistence.JdbcAuditEntryStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "jdbc")
public class AuditJdbcPersistenceConfiguration {

    @Bean
    AuditEntryStore auditEntryStore(DataSource dataSource) {
        return new JdbcAuditEntryStore(new JdbcTemplate(dataSource));
    }
}
