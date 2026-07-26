package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.microsoftconnection.application.SubscriptionLifecycleService;
import com.nanobaseai.actenora.microsoftconnection.application.model.GraphChangeNotification;
import com.nanobaseai.actenora.microsoftconnection.application.port.SubscriptionGateway;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.notification.JdbcNotificationInbox;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence.InMemorySubscriptionStore;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.jdbc.JdbcOutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import com.nanobaseai.actenora.sharedkernel.time.InstantClock;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Proves the Spring proxy around {@code @Transactional handleChangeNotification} opens the
 * transaction itself, so inbox claim and outbox enqueue commit or roll back together.
 */
class GraphNotificationLifecycleTransactionTest {

    @Test
    void handlerFailureRollsBackClaimAndOutboxThroughSpringProxy() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TransactionalGraphTestConfig.class)) {
            SubscriptionLifecycleService lifecycle = context.getBean(SubscriptionLifecycleService.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            JdbcOutboxStore outbox = context.getBean(JdbcOutboxStore.class);

            assertTrue(AopUtils.isAopProxy(lifecycle), "@Transactional requires a Spring proxy");

            GraphChangeNotification notification = new GraphChangeNotification(
                    "notification-tx-1",
                    "sub-1",
                    "updated",
                    "users/organizer@contoso.com/events",
                    "evt-1",
                    "client-state",
                    UUID.randomUUID().toString()
            );

            assertThrows(IllegalStateException.class,
                    () -> lifecycle.handleChangeNotification(notification, n -> {
                        outbox.append(OutboxEvent.pending(envelope(n.subscriptionId(), n.resource()), Instant.now()));
                        throw new IllegalStateException("GRAPH_TENANT_UNMAPPED");
                    }));

            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM microsoftconnection.notification_inbox", Integer.class));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM operations.outbox_event", Integer.class));

            assertTrue(lifecycle.handleChangeNotification(notification, n ->
                    outbox.append(OutboxEvent.pending(envelope(n.subscriptionId(), n.resource()), Instant.now()))));

            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM microsoftconnection.notification_inbox", Integer.class));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM operations.outbox_event", Integer.class));
        }
    }

    private static EventEnvelope envelope(String subscriptionId, String resource) {
        UUID eventId = UUID.randomUUID();
        return new EventEnvelope(
                eventId,
                GraphChangeNotificationProcessor.GRAPH_CHANGE_RECEIVED,
                1,
                Instant.now(),
                TenantId.random(),
                "GraphSubscription",
                subscriptionId,
                eventId,
                null,
                null,
                "microsoft-connection",
                "{\"resource\":\"" + resource + "\"}"
        );
    }

    @Configuration
    @EnableTransactionManagement
    static class TransactionalGraphTestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl(
                    "jdbc:h2:mem:graph-lifecycle-tx-" + UUID.randomUUID()
                            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH");
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("CREATE SCHEMA microsoftconnection");
            jdbc.execute("CREATE SCHEMA operations");
            jdbc.execute("""
                    CREATE TABLE microsoftconnection.notification_inbox (
                        consumer_name VARCHAR(128) NOT NULL,
                        notification_id VARCHAR(256) NOT NULL,
                        claimed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        PRIMARY KEY (consumer_name, notification_id)
                    )
                    """);
            jdbc.execute("""
                    CREATE TABLE operations.outbox_event (
                        id UUID PRIMARY KEY,
                        aggregate_type VARCHAR(128) NOT NULL,
                        aggregate_id VARCHAR(128) NOT NULL,
                        tenant_id UUID NOT NULL,
                        event_type VARCHAR(255) NOT NULL,
                        event_version INT NOT NULL,
                        payload_json CLOB NOT NULL,
                        correlation_id UUID NOT NULL,
                        causation_id UUID,
                        trace_id VARCHAR(128),
                        occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        published_at TIMESTAMP WITH TIME ZONE,
                        status VARCHAR(32) NOT NULL,
                        attempt_count INT NOT NULL,
                        next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        failure_code VARCHAR(64)
                    )
                    """);
            return dataSource;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcOutboxStore outboxStore(DataSource dataSource) {
            return new JdbcOutboxStore(dataSource, "operations", new TenantFairnessTracker());
        }

        @Bean
        SubscriptionLifecycleService subscriptionLifecycleService(JdbcTemplate jdbcTemplate) {
            return new SubscriptionLifecycleService(
                    mock(SubscriptionGateway.class),
                    new InMemorySubscriptionStore(),
                    new JdbcNotificationInbox(jdbcTemplate),
                    InstantClock.systemUTC(),
                    Duration.ofHours(6),
                    Duration.ofHours(48)
            );
        }
    }
}
