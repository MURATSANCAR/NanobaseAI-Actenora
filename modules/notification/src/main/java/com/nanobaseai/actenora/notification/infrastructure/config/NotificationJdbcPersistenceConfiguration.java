package com.nanobaseai.actenora.notification.infrastructure.config;

import com.nanobaseai.actenora.notification.application.port.UserNotificationRepository;
import com.nanobaseai.actenora.notification.infrastructure.persistence.JdbcUserNotificationRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "jdbc")
public class NotificationJdbcPersistenceConfiguration {

    @Bean
    UserNotificationRepository userNotificationRepository(DataSource dataSource) {
        return new JdbcUserNotificationRepository(new JdbcTemplate(dataSource));
    }
}
