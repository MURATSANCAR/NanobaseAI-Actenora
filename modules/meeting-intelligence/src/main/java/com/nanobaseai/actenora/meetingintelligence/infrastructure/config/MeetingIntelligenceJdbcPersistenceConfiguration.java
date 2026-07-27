package com.nanobaseai.actenora.meetingintelligence.infrastructure.config;

import com.nanobaseai.actenora.meetingintelligence.application.ledger.port.LedgerEventStore;
import com.nanobaseai.actenora.meetingintelligence.application.ledger.port.LedgerProjectionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.ActionItemRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.ArtifactMetadataStorePort;
import com.nanobaseai.actenora.meetingintelligence.application.port.CommitmentRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.DecisionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.EvidenceLinkRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingKnowledgeStorePort;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingNoteRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingNoteVersionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.OpenQuestionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.QualityFlagRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.RiskRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger.JdbcLedgerEventStore;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger.JdbcLedgerProjectionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.JdbcActionItemRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.JdbcArtifactMetadataStore;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.JdbcCommitmentRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.JdbcDecisionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.JdbcEvidenceLinkRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.JdbcMeetingKnowledgeStore;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.JdbcMeetingNoteRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.JdbcMeetingNoteVersionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.JdbcOpenQuestionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.JdbcQualityFlagRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.JdbcRiskRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "jdbc")
public class MeetingIntelligenceJdbcPersistenceConfiguration {

    @Bean
    MeetingNoteRepository meetingNoteRepository(DataSource dataSource) {
        return new JdbcMeetingNoteRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    MeetingNoteVersionRepository meetingNoteVersionRepository(DataSource dataSource) {
        return new JdbcMeetingNoteVersionRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    DecisionRepository decisionRepository(DataSource dataSource) {
        return new JdbcDecisionRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    ActionItemRepository actionItemRepository(DataSource dataSource) {
        return new JdbcActionItemRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    RiskRepository riskRepository(DataSource dataSource) {
        return new JdbcRiskRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    CommitmentRepository commitmentRepository(DataSource dataSource) {
        return new JdbcCommitmentRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    OpenQuestionRepository openQuestionRepository(DataSource dataSource) {
        return new JdbcOpenQuestionRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    EvidenceLinkRepository evidenceLinkRepository(DataSource dataSource) {
        return new JdbcEvidenceLinkRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    QualityFlagRepository qualityFlagRepository(DataSource dataSource) {
        return new JdbcQualityFlagRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    LedgerEventStore ledgerEventStore(DataSource dataSource) {
        return new JdbcLedgerEventStore(new JdbcTemplate(dataSource));
    }

    @Bean
    LedgerProjectionRepository ledgerProjectionRepository(DataSource dataSource) {
        return new JdbcLedgerProjectionRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    MeetingKnowledgeStorePort meetingKnowledgeStore(DataSource dataSource) {
        return new JdbcMeetingKnowledgeStore(new JdbcTemplate(dataSource));
    }

    @Bean
    ArtifactMetadataStorePort artifactMetadataStore(DataSource dataSource) {
        return new JdbcArtifactMetadataStore(new JdbcTemplate(dataSource));
    }
}
