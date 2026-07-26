package com.nanobaseai.actenora.meetingintelligence.infrastructure.config;

import com.nanobaseai.actenora.meetingintelligence.application.ledger.port.LedgerEventStore;
import com.nanobaseai.actenora.meetingintelligence.application.ledger.port.LedgerProjectionRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingNoteRepository;
import com.nanobaseai.actenora.meetingintelligence.application.port.MeetingNoteVersionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger.JdbcLedgerEventStore;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.ledger.JdbcLedgerProjectionRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.JdbcMeetingNoteRepository;
import com.nanobaseai.actenora.meetingintelligence.infrastructure.persistence.JdbcMeetingNoteVersionRepository;
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
    LedgerEventStore ledgerEventStore(DataSource dataSource) {
        return new JdbcLedgerEventStore(new JdbcTemplate(dataSource));
    }

    @Bean
    LedgerProjectionRepository ledgerProjectionRepository(DataSource dataSource) {
        return new JdbcLedgerProjectionRepository(new JdbcTemplate(dataSource));
    }
}
