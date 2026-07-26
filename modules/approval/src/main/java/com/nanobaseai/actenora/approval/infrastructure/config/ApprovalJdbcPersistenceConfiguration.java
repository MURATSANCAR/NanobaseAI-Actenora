package com.nanobaseai.actenora.approval.infrastructure.config;

import com.nanobaseai.actenora.approval.application.port.ApprovalRequestRepository;
import com.nanobaseai.actenora.approval.application.port.ParticipantDisputeRepository;
import com.nanobaseai.actenora.approval.infrastructure.persistence.JdbcApprovalRequestRepository;
import com.nanobaseai.actenora.approval.infrastructure.persistence.JdbcParticipantDisputeRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "jdbc")
public class ApprovalJdbcPersistenceConfiguration {

    @Bean
    ApprovalRequestRepository approvalRequestRepository(DataSource dataSource) {
        return new JdbcApprovalRequestRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    ParticipantDisputeRepository participantDisputeRepository(DataSource dataSource) {
        return new JdbcParticipantDisputeRepository(new JdbcTemplate(dataSource));
    }
}
