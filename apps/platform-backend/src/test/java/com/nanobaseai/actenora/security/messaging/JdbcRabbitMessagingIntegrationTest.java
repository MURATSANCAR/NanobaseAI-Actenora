package com.nanobaseai.actenora.security.messaging;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.EventBackbone;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.EventMessagingConfig;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxStatus;
import com.nanobaseai.actenora.sharedkernel.messaging.broker.RabbitDlxTopology;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.jdbc.JdbcDeadLetterStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.jdbc.JdbcInboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.jdbc.JdbcOutboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.support.QueueDepthGuard;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
class JdbcRabbitMessagingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("actenora")
            .withUsername("actenora")
            .withPassword("actenora");

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    private static DataSource dataSource;
    private static RabbitTemplate rabbitTemplate;
    private EventBackbone backbone;
    private Instant now;

    @BeforeAll
    static void initInfrastructure() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUsername(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        dataSource = ds;
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS operations");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS operations.outbox_event (
                        id               UUID PRIMARY KEY,
                        aggregate_type   VARCHAR(128) NOT NULL,
                        aggregate_id     VARCHAR(128) NOT NULL,
                        tenant_id        UUID NOT NULL,
                        event_type       VARCHAR(255) NOT NULL,
                        event_version    INT NOT NULL,
                        payload_json     TEXT NOT NULL,
                        correlation_id   UUID NOT NULL,
                        causation_id     UUID,
                        trace_id         VARCHAR(128),
                        occurred_at      TIMESTAMPTZ NOT NULL,
                        published_at     TIMESTAMPTZ,
                        status           VARCHAR(32) NOT NULL,
                        attempt_count    INT NOT NULL DEFAULT 0,
                        next_attempt_at  TIMESTAMPTZ NOT NULL,
                        failure_code     VARCHAR(64),
                        created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS operations.inbox_event (
                        consumer_name    VARCHAR(128) NOT NULL,
                        event_id         UUID NOT NULL,
                        received_at      TIMESTAMPTZ NOT NULL,
                        processed_at     TIMESTAMPTZ,
                        status           VARCHAR(32) NOT NULL,
                        failure_code     VARCHAR(64),
                        PRIMARY KEY (consumer_name, event_id)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS operations.dead_letter_event (
                        id               UUID PRIMARY KEY,
                        source           VARCHAR(32) NOT NULL,
                        event_id         UUID NOT NULL,
                        consumer_name    VARCHAR(128),
                        event_type       VARCHAR(255) NOT NULL,
                        event_version    INT NOT NULL,
                        payload_json     TEXT NOT NULL,
                        failure_code     VARCHAR(64) NOT NULL,
                        failure_detail   TEXT,
                        correlation_id   UUID,
                        tenant_id        UUID,
                        attempts         INT NOT NULL,
                        dead_lettered_at TIMESTAMPTZ NOT NULL,
                        replayed_at      TIMESTAMPTZ
                    )
                    """);
        }

        CachingConnectionFactory connectionFactory = new CachingConnectionFactory(
                rabbit.getHost(), rabbit.getAmqpPort());
        connectionFactory.setUsername(rabbit.getAdminUsername());
        connectionFactory.setPassword(rabbit.getAdminPassword());
        rabbitTemplate = new RabbitTemplate(connectionFactory);

        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        TopicExchange domain = new TopicExchange(RabbitDlxTopology.EVENTS_EXCHANGE, true, false);
        admin.declareExchange(domain);

        Queue transcriptQueue = QueueBuilder.durable(RabbitDlxTopology.consumerQueue("transcript"))
                .deadLetterExchange(RabbitDlxTopology.DLX_EXCHANGE)
                .deadLetterRoutingKey(RabbitDlxTopology.deadLetterRoutingKey("transcript"))
                .build();
        admin.declareQueue(transcriptQueue);
        admin.declareBinding(BindingBuilder.bind(transcriptQueue)
                .to(domain)
                .with("meeting.MeetingOccurrenceUpserted.v1"));
    }

    @BeforeEach
    void setUp() throws Exception {
        now = Instant.parse("2026-07-26T10:00:00Z");
        TenantFairnessTracker fairness = new TenantFairnessTracker();
        JdbcOutboxStore outboxStore = new JdbcOutboxStore(dataSource, "operations", fairness);
        JdbcInboxStore inboxStore = new JdbcInboxStore(dataSource, "operations");
        JdbcDeadLetterStore deadLetterStore = new JdbcDeadLetterStore(dataSource, "operations");
        RabbitEventTransport transport = new RabbitEventTransport(rabbitTemplate);
        backbone = EventBackbone.of(
                EventMessagingConfig.defaults("platform-it"),
                outboxStore,
                inboxStore,
                deadLetterStore,
                transport,
                fairness,
                new QueueDepthGuard(100)
        );

        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("TRUNCATE operations.outbox_event");
            st.execute("TRUNCATE operations.inbox_event");
            st.execute("TRUNCATE operations.dead_letter_event");
        }
        rabbitTemplate.execute(channel -> {
            channel.queuePurge(RabbitDlxTopology.consumerQueue("transcript"));
            return null;
        });
    }

    @Test
    void relayPublishesOutboxEventToRabbitQueue() {
        UUID eventId = UUID.randomUUID();
        TenantId tenantId = TenantId.random();
        EventEnvelope envelope = new EventEnvelope(
                eventId,
                "meeting.MeetingOccurrenceUpserted.v1",
                1,
                now,
                tenantId,
                "MeetingOccurrence",
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                null,
                "trace-it",
                "platform-it",
                "{\"occurrenceId\":\"" + UUID.randomUUID() + "\"}"
        );
        OutboxEvent pending = OutboxEvent.pending(envelope, now);
        backbone.outboxStore().append(pending);

        int published = backbone.relay().publishDueBatch();
        assertEquals(1, published);
        assertEquals(
                OutboxStatus.PUBLISHED,
                backbone.outboxStore().findById(eventId).orElseThrow().status()
        );

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Message message = rabbitTemplate.receive(RabbitDlxTopology.consumerQueue("transcript"));
            assertNotNull(message);
            EventEnvelope received = RabbitEventTransport.toEnvelope(message);
            assertEquals(eventId, received.eventId());
            assertTrue(received.payloadJson().contains("occurrenceId"));
        });
    }
}
