package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.microsoftconnection.application.model.GraphSubscription;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.notification.JdbcNotificationInbox;
import com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence.JdbcSubscriptionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone A — durable Graph edge: inbox claim idempotency and subscription restart survival.
 */
@Testcontainers(disabledWithoutDocker = true)
class GraphJdbcEdgePersistenceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("actenora")
            .withUsername("actenora")
            .withPassword("actenora");

    private JdbcTemplate jdbc;
    private JdbcNotificationInbox inbox;
    private JdbcSubscriptionStore subscriptions;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUsername(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS microsoftconnection");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS microsoftconnection.notification_inbox (
                  consumer_name VARCHAR(128) NOT NULL,
                  notification_id VARCHAR(512) NOT NULL,
                  claimed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                  PRIMARY KEY (consumer_name, notification_id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS microsoftconnection.graph_subscription (
                  tenant_id UUID NOT NULL,
                  subscription_id VARCHAR(256) NOT NULL,
                  resource TEXT NOT NULL,
                  change_type VARCHAR(64),
                  notification_url TEXT,
                  client_state TEXT,
                  expiration_date_time TIMESTAMPTZ NOT NULL,
                  application_id VARCHAR(128),
                  PRIMARY KEY (tenant_id, subscription_id)
                )
                """);
        inbox = new JdbcNotificationInbox(jdbc);
        subscriptions = new JdbcSubscriptionStore(jdbc);
    }

    @Test
    void claimIsIdempotentAcrossSecondSight() {
        assertTrue(inbox.claim("graph-change-notification", "sub::created::evt-1"));
        assertFalse(inbox.claim("graph-change-notification", "sub::created::evt-1"));
    }

    @Test
    void subscriptionSurvivesStoreReload() {
        UUID tenantId = UUID.randomUUID();
        GraphSubscription saved = new GraphSubscription(
                tenantId,
                "sub-restart-1",
                "users/u1/events",
                "created,updated",
                "https://example.test/hooks",
                "client-state",
                Instant.now().plusSeconds(3600),
                "app-1"
        );
        subscriptions.save(saved);

        JdbcSubscriptionStore reloaded = new JdbcSubscriptionStore(jdbc);
        assertEquals(1, reloaded.findAllForTenant(tenantId).size());
        assertEquals("sub-restart-1", reloaded.findById(tenantId, "sub-restart-1").orElseThrow().subscriptionId());
    }

    @Test
    void calendarCursorSurvivesStoreReload() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS microsoftconnection.calendar_sync_cursor (
                  tenant_id UUID NOT NULL,
                  user_id VARCHAR(320) NOT NULL,
                  delta_link TEXT,
                  next_link TEXT,
                  updated_at TIMESTAMPTZ NOT NULL,
                  PRIMARY KEY (tenant_id, user_id)
                )
                """);
        var cursors = new com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence.JdbcCalendarSyncCursorStore(jdbc);
        UUID tenantId = UUID.randomUUID();
        cursors.save(new com.nanobaseai.actenora.microsoftconnection.application.model.CalendarSyncCursor(
                tenantId,
                "user@contoso.com",
                "https://graph.microsoft.com/delta-link",
                null,
                Instant.now()
        ));
        var reloaded = new com.nanobaseai.actenora.microsoftconnection.infrastructure.persistence.JdbcCalendarSyncCursorStore(jdbc);
        assertTrue(reloaded.find(tenantId, "user@contoso.com").isPresent());
        assertEquals(
                "https://graph.microsoft.com/delta-link",
                reloaded.find(tenantId, "user@contoso.com").orElseThrow().deltaLinkOptional().orElseThrow()
        );
    }
}
