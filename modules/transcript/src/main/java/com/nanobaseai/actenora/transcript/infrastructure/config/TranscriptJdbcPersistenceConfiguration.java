package com.nanobaseai.actenora.transcript.infrastructure.config;

import com.nanobaseai.actenora.transcript.application.port.out.TranscriptRepository;
import com.nanobaseai.actenora.transcript.application.port.out.TranscriptSegmentRepository;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.JdbcTranscriptRepository;
import com.nanobaseai.actenora.transcript.infrastructure.persistence.JdbcTranscriptSegmentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "jdbc")
public class TranscriptJdbcPersistenceConfiguration {

    @Bean
    TranscriptRepository transcriptRepository(DataSource dataSource) {
        return new JdbcTranscriptRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    TranscriptSegmentRepository transcriptSegmentRepository(DataSource dataSource) {
        return new JdbcTranscriptSegmentRepository(new JdbcTemplate(dataSource));
    }
}
