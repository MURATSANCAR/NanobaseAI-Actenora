package com.nanobaseai.actenora.notification.config;

import com.nanobaseai.actenora.notification.api.NotificationApi;
import com.nanobaseai.actenora.notification.application.UserNotificationService;
import com.nanobaseai.actenora.notification.application.port.UserNotificationRepository;
import com.nanobaseai.actenora.notification.infrastructure.persistence.InMemoryUserNotificationRepository;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationConfiguration {

    @Bean
    @ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "inmemory", matchIfMissing = true)
    @ConditionalOnMissingBean(UserNotificationRepository.class)
    UserNotificationRepository inMemoryUserNotificationRepository() {
        return new InMemoryUserNotificationRepository();
    }

    @Bean
    @ConditionalOnMissingBean(NotificationApi.class)
    NotificationApi notificationApi(UserNotificationRepository repository, InstantClock clock) {
        return new UserNotificationService(repository, clock);
    }
}
