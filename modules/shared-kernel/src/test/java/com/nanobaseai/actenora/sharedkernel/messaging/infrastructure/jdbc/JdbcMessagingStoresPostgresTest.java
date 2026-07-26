package com.nanobaseai.actenora.sharedkernel.messaging.infrastructure.jdbc;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.sharedkernel.messaging.DeadLetterEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.EventEnvelope;
import com.nanobaseai.actenora.sharedkernel.messaging.InboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.InboxStatus;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxEvent;
import com.nanobaseai.actenora.sharedkernel.messaging.OutboxStatus;
import com.nanobaseai.actenora.sharedkernel.messaging.port.InboxStore;
import com.nanobaseai.actenora.sharedkernel.messaging.support.TenantFairnessTracker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class JdbcMessagingStoresPostgresTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("actenora")
            .withUsername("actenora")
            .withPassword("actenora");

    private static DataSource dataSource;
    private TenantFairnessTracker fairness;
    private JdbcOutboxStore outboxStore;
    private JdbcInboxStore inboxStore;
    private JdbcDeadLetterStore deadLetterStore;
    private Instant now;

    @BeforeAll
    static void initSchema() throws Exception {
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
    }

    @BeforeEach
    void setUp() throws Exception {
        fairness = new TenantFairnessTracker();
        outboxStore = new JdbcOutboxStore(dataSource, "operations", fairness);
        inboxStore = new JdbcInboxStore(dataSource, "operations");
        deadLetterStore = new JdbcDeadLetterStore(dataSource, "operations");
        now = Instant.parse("2026-07-26T10:00:00Z");
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            st.execute("TRUNCATE operations.outbox_event");
            st.execute("TRUNCATE operations.inbox_event");
            st.execute("TRUNCATE operations.dead_letter_event");
        }
    }

    @Test
    void outboxAppendClaimAndSaveRoundTrip() {
        OutboxEvent pending = OutboxEvent.pending(sampleEnvelope(TenantId.random()), now);
        outboxStore.append(pending);

        List<OutboxEvent> claimed = outboxStore.claimDue(now, 10);
        assertEquals(1, claimed.size());
        assertEquals(OutboxStatus.PUBLISHING, claimed.getFirst().status());

        OutboxEvent claimedEvent = claimed.getFirst();
        claimedEvent.markPublished(now);
        outboxStore.save(claimedEvent);

        OutboxEvent stored = outboxStore.findById(pending.id()).orElseThrow();
        assertEquals(OutboxStatus.PUBLISHED, stored.status());
        assertTrue(stored.publishedAt().isPresent());
    }

    @Test
    void outboxClaimUsesTenantFairnessAcrossTenants() {
        TenantId tenantA = TenantId.random();
        TenantId tenantB = TenantId.random();
        outboxStore.append(OutboxEvent.pending(sampleEnvelope(tenantA), now));
        outboxStore.append(OutboxEvent.pending(sampleEnvelope(tenantB), now));

        List<OutboxEvent> claimed = outboxStore.claimDue(now, 2);
        assertEquals(2, claimed.size());
        assertFalse(claimed.get(0).tenantId().equals(claimed.get(1).tenantId()));
    }

    @Test
    void inboxClaimIsIdempotent() {
        UUID eventId = UUID.randomUUID();
        InboxEvent first = InboxEvent.received("transcript", eventId, now);
        assertEquals(InboxStore.ClaimOutcome.INSERTED, inboxStore.claim(first).outcome());

        InboxEvent duplicate = InboxEvent.received("transcript", eventId, now);
        assertEquals(InboxStore.ClaimOutcome.DUPLICATE, inboxStore.claim(duplicate).outcome());
    }

    @Test
    void inboxSaveUpdatesStatus() {
        UUID eventId = UUID.randomUUID();
        InboxEvent received = InboxEvent.received("transcript", eventId, now);
        inboxStore.claim(received);

        InboxEvent stored = inboxStore.find("transcript", eventId).orElseThrow();
        stored.markProcessed(now);
        inboxStore.save(stored);

        assertEquals(InboxStatus.PROCESSED, inboxStore.find("transcript", eventId).orElseThrow().status());
    }

    @Test
    void deadLetterAppendFindAndReplay() {
        UUID eventId = UUID.randomUUID();
        DeadLetterEvent dlq = new DeadLetterEvent(
                UUID.randomUUID(),
                DeadLetterEvent.DeadLetterSource.OUTBOX,
                eventId,
                null,
                "meeting.MeetingCreated.v1",
                1,
                "{}",
                "MAX_ATTEMPTS",
                "exhausted",
                UUID.randomUUID(),
                TenantId.random(),
                8,
                now,
                null
        );
        deadLetterStore.append(dlq);

        assertTrue(deadLetterStore.findByEventId(eventId).isPresent());
        assertEquals(1, deadLetterStore.listOpen(10).size());

        DeadLetterEvent replayed = dlq.markReplayed(now);
        deadLetterStore.save(replayed);
        assertTrue(deadLetterStore.listOpen(10).isEmpty());
    }

    private static EventEnvelope sampleEnvelope(TenantId tenantId) {
        return new EventEnvelope(
                UUID.randomUUID(),
                "meeting.MeetingCreated.v1",
                1,
                Instant.parse("2026-07-26T09:00:00Z"),
                tenantId,
                "MeetingOccurrence",
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                null,
                null,
                "meeting",
                "{\"ok\":true}"
        );
    }
}
