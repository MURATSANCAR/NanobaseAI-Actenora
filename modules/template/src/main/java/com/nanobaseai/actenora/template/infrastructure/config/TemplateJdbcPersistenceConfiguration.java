package com.nanobaseai.actenora.template.infrastructure.config;

import com.nanobaseai.actenora.template.application.port.out.MeetingTemplateRepository;
import com.nanobaseai.actenora.template.infrastructure.persistence.JdbcMeetingTemplateRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "jdbc")
public class TemplateJdbcPersistenceConfiguration {

    @Bean
    MeetingTemplateRepository meetingTemplateRepository(DataSource dataSource) {
        return new JdbcMeetingTemplateRepository(new JdbcTemplate(dataSource));
    }
}
