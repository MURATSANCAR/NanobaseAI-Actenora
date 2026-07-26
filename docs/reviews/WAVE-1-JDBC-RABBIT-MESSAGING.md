# Wave 1 — JDBC/Rabbit messaging (enterprise prod master plan)

## Scope

Durable transactional outbox/inbox/DLQ backed by PostgreSQL, RabbitMQ transport for relay, queue topology aligned to real consumers, relay backpressure, and Testcontainers integration tests.

## Components

### shared-kernel JDBC adapters

| Class | Table | Notes |
|-------|-------|-------|
| `JdbcOutboxStore` | `<schema>.outbox_event` | `claimDue` uses `SELECT … FOR UPDATE SKIP LOCKED`, tenant fairness, marks `PUBLISHING` |
| `JdbcInboxStore` | `<schema>.inbox_event` | `INSERT … ON CONFLICT DO NOTHING` idempotency claim |
| `JdbcDeadLetterStore` | `<schema>.dead_letter_event` | append/find/listOpen/save |
| `JdbcMessagingSchema` | — | schema identifier whitelist `[a-z][a-z0-9_]*` |

DDL is identical across all 14 module schemas (`V*__event_backbone_outbox_inbox_dlq.sql`).

### Platform transport & wiring

| Class | Role |
|-------|------|
| `RabbitEventTransport` | Publishes to `actenora.domain` with routing key = event type; sets `HEADER_*` plus aggregate/occurred-at/producer headers |
| `JdbcRabbitMessagingPlatformConfiguration` | Active when `actenora.messaging.mode=jdbc-rabbit`; registers JDBC stores, Rabbit transport, `EventBackbone`, relay start, `@RabbitListener` consumers |
| `EventBackboneConsumerDispatch` | Shared transcript + meeting-intelligence dispatch (InMemory fan-out and Rabbit inbound) |

`EventBackbonePlatformConfiguration` (InMemory) remains default and yields via `@ConditionalOnMissingBean`.

### Relay backpressure

`PollingOutboxPublisher` accepts optional `QueueDepthGuard`. When configured, relay skips batches when pending outbox depth reaches `actenora.messaging.relay.max-queue-depth` (default `10000`).

### Rabbit topology

Added to `infrastructure/rabbitmq/definitions.json`:

- `actenora.transcript.events` ← `meeting.MeetingOccurrenceUpserted.v1`, `meeting.#`
- `actenora.meeting-intelligence.events` ← `meetingintelligence.NoteApprovedForLedger.v1`, `meetingintelligence.#`

Event type strings match `MeetingIntegrationEvents` and `MeetingIntelligenceIntegrationEvents`.

## Configuration

```yaml
actenora:
  messaging:
    mode: inmemory          # default
    jdbc:
      schema: operations    # outbox/inbox/dlq schema
    relay:
      max-queue-depth: 10000
```

Integration profile (`application-it.yml`) sets `mode: jdbc-rabbit`.

## Tests

| Test | Containers | Validates |
|------|------------|-----------|
| `JdbcMessagingStoresPostgresTest` | Postgres | JDBC outbox claim/fairness, inbox idempotency, DLQ replay |
| `JdbcRabbitMessagingIntegrationTest` | Postgres + RabbitMQ | Outbox append → relay → message on transcript queue |

Run (requires Docker):

```bash
mvn -pl modules/shared-kernel test -Dtest=JdbcMessagingStoresPostgresTest
mvn -pl apps/platform-backend test -Dtest=JdbcRabbitMessagingIntegrationTest
```

## Exit criteria

- [x] JDBC adapters implement all port methods with safe schema quoting
- [x] `RabbitEventTransport` publishes to `actenora.domain` with routing key = event type
- [x] `jdbc-rabbit` mode wires platform backbone; InMemory yields
- [x] Transcript + meeting-intelligence queues/bindings in definitions.json
- [x] `QueueDepthGuard` on relay
- [x] Testcontainers IT for Postgres (+ Rabbit)
- [x] `actenora.messaging.mode` in `application.yml`

## Next (Wave 2+)

- Per-module schema routing (meeting outbox in `meeting`, transcript inbox in `transcript`, etc.)
- Publisher confirms / mandatory publish
- Rabbit consumer error handler → DB DLQ
- CDC/log-tail relay replacing polling
