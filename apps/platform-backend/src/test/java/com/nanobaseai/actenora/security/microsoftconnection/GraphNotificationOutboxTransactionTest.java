package com.nanobaseai.actenora.security.microsoftconnection;

import com.nanobaseai.actenora.microsoftconnection.infrastructure.notification.JdbcNotificationInbox;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.jdbc.JdbcOutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraphNotificationOutboxTransactionTest {

    @Test
    void claimAndOutboxAppendRollbackTogether() {
        var dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
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

        JdbcNotificationInbox inbox = new JdbcNotificationInbox(jdbc);
        JdbcOutboxStore outbox = new JdbcOutboxStore(dataSource, "operations", new TenantFairnessTracker());
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        TenantId tenantId = TenantId.random();
        Instant now = Instant.now();
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                GraphChangeNotificationProcessor.GRAPH_CHANGE_RECEIVED,
                1,
                now,
                tenantId,
                "GraphSubscription",
                "sub-1",
                UUID.randomUUID(),
                null,
                null,
                "microsoft-connection",
                "{\"resource\":\"users/user@contoso.com/events\"}");

        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            inbox.claim("graph-change-notification", "notification-1");
            outbox.append(OutboxEvent.pending(envelope, now));
            throw new IllegalStateException("simulate handler failure");
        }));

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM microsoftconnection.notification_inbox", Integer.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operations.outbox_event", Integer.class));
    }
}
