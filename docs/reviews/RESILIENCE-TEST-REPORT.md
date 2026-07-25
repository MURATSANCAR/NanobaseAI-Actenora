# RESILIENCE-TEST-REPORT

**Phase:** 28 — Load, resilience & failure tests  
**Date:** 2026-07-25  
**Raw capture:** [`artifacts/phase-28/surefire-faz28.log`](../../artifacts/phase-28/surefire-faz28.log)

## 1. Executive verdict

Failure-injection scenarios for messaging, storage, Graph/mail, AI pipeline, and workers all meet FAZ 28 acceptance: **no data loss**, **no duplicate side effects**, **DLQ recovery works**, and **graceful shutdown** rejects new work while preserving pending outbox rows.

## 2. Scenario matrix

| Scenario | Harness | Result |
|----------|---------|--------|
| RabbitMQ restart (transport down → up) | `MessagingResilienceScenarioTest.rabbitMqRestart_*` | PASS |
| PostgreSQL temporary failure | `postgresTemporaryFailure_*` (`TransientFailureOutboxStore`) | PASS |
| MinIO failure → recovery | `MinioFailureScenarioTest` | PASS |
| AI invalid JSON | `ModelFailoverAndAiFailureScenarioTest.invalidJson_*` + pipeline suite | PASS |
| Context overflow | `contextOverflow_*` | PASS |
| Graph 429 + Retry-After | `GraphMailResilienceScenarioTest.graph429_*` | PASS |
| Mail rate limit | `mailRateLimit_*` | PASS |
| Duplicate Graph notification | `duplicateGraphNotification_*` (`InMemoryNotificationInbox`) | PASS |
| Duplicate integration event | `duplicateIntegrationEvent_*` (inbox) | PASS |
| Worker restart / drain | `workerRestart_drainRejectsNewWork*` + messaging redelivery | PASS |
| Graceful shutdown | `gracefulShutdown_*` | PASS |
| Queue backlog | `queueBacklog_guardPreventsUncontrolledGrowth` | PASS |
| DLQ recovery | `dlqRecovery_replaysAndCompletes` | PASS |
| Tenant quota exceeded | `QuotaUnderLoadScenarioTest` | PASS |

## 3. Mechanisms under test

| Concern | Implementation |
|---------|----------------|
| Broker outage | `RecordingEventTransport.failWhen` — outbox remains PENDING/RETRY until broker recovers |
| DB blip | `TransientFailureOutboxStore.armFailures(n)` then resume |
| Object store timeout | `InMemoryObjectStorage.forceTimeout` → `OBJECT_STORAGE_TIMEOUT`; retry succeeds; duplicate hash skips second record |
| Graph throttle | `GraphHttpClient` honors 429 + `Retry-After` (JDK `HttpServer` fixture) |
| Mail throttle | `RateLimitedMailGateway` → `GRAPH_RATE_LIMITED` / 429; idempotency key prevents double send |
| Poison / exhausted | Outbox → DEAD_LETTER; `EventReplayer.replayOutbox` → PENDING → PUBLISHED |
| Shutdown | `GracefulShutdownGate` / `EventBackbone.close()` → relay publishes 0; consumers `REJECTED_SHUTDOWN` |
| Backpressure | `QueueDepthGuard` + Rabbit definitions `overflow: reject-publish` |

## 4. Acceptance criteria

| Criterion | Met? | Evidence |
|-----------|------|----------|
| Veri kaybı yok | Yes | Outbox survives broker/DB blips; MinIO recovery stores once |
| Duplicate business record yok | Yes | Inbox + notification inbox + mail idempotency + transcript hash |
| Kuyruklar kontrolsüz büyümüyor | Yes | Depth guard rejects over max |
| DLQ recovery çalışıyor | Yes | Replay then successful publish |
| SLA breach ölçülüyor | Yes | See LOAD + `SlaBreachTracker` |
| Critical starvation yok | Yes | See LOAD fairness tests |

## 5. Risks

| Risk | Mitigation |
|------|------------|
| Concurrent monorepo agents truncating `shared-kernel/target` | `scripts/run-faz28-tests` runs module-by-module with shared-kernel reinstall |
| No live Rabbit/Postgres chaos yet | Ports + in-memory chaos cover semantics; compose chaos deferred |

## 6. How to re-run

```bash
make faz28
```
