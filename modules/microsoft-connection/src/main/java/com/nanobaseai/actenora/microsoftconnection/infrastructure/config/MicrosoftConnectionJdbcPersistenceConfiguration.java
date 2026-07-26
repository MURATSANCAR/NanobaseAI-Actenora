package com.nanobaseai.actenora.microsoftconnection.infrastructure.config;

import com.nanobaseai.actenora.microsoftconnection.application.port.CalendarSyncCursorStore;
import com.nanobaseai.actenora.microsoftconnection.application.port.NotificationInbox;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionStore;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.notification.JdbcNotificationInbox;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence.JdbcCalendarSyncCursorStore;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence.JdbcSubscriptionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "jdbc")
@ConditionalOnExpression("'${actenora.microsoft-graph.enabled:false}' == 'true'")
public class MicrosoftConnectionJdbcPersistenceConfiguration {

    @Bean
    SubscriptionStore jdbcSubscriptionStore(DataSource dataSource) {
        return new JdbcSubscriptionStore(new JdbcTemplate(dataSource));
    }

    @Bean
    NotificationInbox jdbcNotificationInbox(DataSource dataSource) {
        return new JdbcNotificationInbox(new JdbcTemplate(dataSource));
    }

    @Bean
    CalendarSyncCursorStore jdbcCalendarSyncCursorStore(DataSource dataSource) {
        return new JdbcCalendarSyncCursorStore(new JdbcTemplate(dataSource));
    }
}
