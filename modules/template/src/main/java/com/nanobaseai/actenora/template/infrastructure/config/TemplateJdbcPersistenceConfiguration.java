package com.nanobaseai.actenora.template.infrastructure.config;

import com.nanobaseai.actenora.template.application.port.out.MeetingTemplateRepository;
import com.nanobaseai.actenora.template.application.port.out.NoteTemplateLockRepository;
import com.nanobaseai.actenora.template.application.port.out.RenderJobRepository;
import com.nanobaseai.actenora.template.application.port.out.RenderedDocumentRepository;
import com.nanobaseai.actenora.template.infrastructure.persistence.JdbcMeetingTemplateRepository;
import com.nanobaseai.actenora.template.infrastructure.persistence.JdbcNoteTemplateLockRepository;
import com.nanobaseai.actenora.template.infrastructure.persistence.JdbcRenderJobRepository;
import com.nanobaseai.actenora.template.infrastructure.persistence.JdbcRenderedDocumentRepository;
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

    @Bean
    NoteTemplateLockRepository noteTemplateLockRepository(DataSource dataSource) {
        return new JdbcNoteTemplateLockRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    RenderJobRepository renderJobRepository(DataSource dataSource) {
        return new JdbcRenderJobRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    RenderedDocumentRepository renderedDocumentRepository(DataSource dataSource) {
        return new JdbcRenderedDocumentRepository(new JdbcTemplate(dataSource));
    }
}
