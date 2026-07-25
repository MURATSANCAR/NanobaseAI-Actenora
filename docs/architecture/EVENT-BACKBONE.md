# EVENT-BACKBONE

**Status:** Implemented (FAZ 10)  
**Date:** 2026-07-25  
**ADRs:** ADR-004 (outbox/inbox), ADR-008 (RabbitMQ / DLX)

## Purpose

Reliable, extraction-ready event infrastructure for the modular monolith:

- Transactional outbox (state + event in one DB TX)
- Polling publisher with CDC-ready `EventTransport` port
- Inbox idempotency per consumer
- Retry classification + exponential backoff with jitter
- Broker DLX naming + durable `dead_letter_event` store
- Correlation / causation / trace propagation
- Schema / version / payload-size validation
- Graceful shutdown gates
- Tenant-fair outbox claiming
- Safe operator replay (`EventReplayer`)

## Code location

`modules/shared-kernel/.../messaging/`

| Component | Role |
|-----------|------|
| `TransactionalOutboxPublisher` | Enqueue only (no broker I/O) |
| `PollingOutboxPublisher` | Relay due rows → `EventTransport` |
| `IdempotentEventConsumer` | Validate → claim inbox → handle → commit |
| `EventTransport` | Broker/CDC adapter seam |
| `RabbitDlxTopology` | Exchange/queue/DLQ naming + header keys |
| `EventReplayer` | Dry-run + reason-gated replay |

## Delivery semantics

| Stage | Guarantee |
|-------|-----------|
| Aggregate write + outbox append | Atomic (same TX) |
| Outbox → broker | At-least-once (crash after publish → `PUBLISHING` reclaim) |
| Consumer effects | Exactly-once *effects* via inbox key |

## Config knobs (`EventMessagingConfig`)

- `maxPayloadBytes` (default 256 KiB) — large payload rejection
- `maxAttempts` — retry then DLQ
- `consumerConcurrency` — handler pool / semaphore
- `publishBatchSize` / `pollInterval`
- `supportedVersions`
- backoff base/cap + jitter

## Replay safety

1. Operator + reason required  
2. Dry-run supported  
3. Outbox: only `DEAD_LETTER` / `PUBLISHED` → reset to `PENDING` (same `event_id`)  
4. Inbox: refuse replay of `PROCESSED`  
5. DLQ row marked `replayed_at`
