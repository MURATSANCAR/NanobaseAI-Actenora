package com.nanobaseai.actenora.meeting.infrastructure.config;

import com.nanobaseai.actenora.meeting.application.port.BusinessContextRepository;
import com.nanobaseai.actenora.meeting.application.port.MeetingOccurrenceRepository;
import com.nanobaseai.actenora.meeting.application.port.MeetingParticipantRepository;
import com.nanobaseai.actenora.meeting.application.port.MeetingSeriesRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.JdbcBusinessContextRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.JdbcMeetingOccurrenceRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.JdbcMeetingParticipantRepository;
import com.nanobaseai.actenora.meeting.infrastructure.persistence.JdbcMeetingSeriesRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "jdbc")
public class MeetingJdbcPersistenceConfiguration {

    @Bean
    BusinessContextRepository businessContextRepository(DataSource dataSource) {
        return new JdbcBusinessContextRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    MeetingSeriesRepository meetingSeriesRepository(DataSource dataSource) {
        return new JdbcMeetingSeriesRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    MeetingOccurrenceRepository meetingOccurrenceRepository(DataSource dataSource) {
        return new JdbcMeetingOccurrenceRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    MeetingParticipantRepository meetingParticipantRepository(DataSource dataSource) {
        return new JdbcMeetingParticipantRepository(new JdbcTemplate(dataSource));
    }
}
