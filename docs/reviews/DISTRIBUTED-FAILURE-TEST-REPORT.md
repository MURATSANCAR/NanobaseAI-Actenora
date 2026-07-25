# DISTRIBUTED-FAILURE-TEST-REPORT

**Phase:** FAZ 29  
**Date:** 2026-07-25  
**Related:** [`DISTRIBUTED-WORKFLOWS.md`](../architecture/DISTRIBUTED-WORKFLOWS.md), ADR-003, ADR-004, ADR-008

## Verdict

**Distributed failure posture is designed, not proven in production-like topology.**

Unit/scenario tests cover pieces of resilience (outbox/inbox, queue depth guard, worker restart idempotency, Graph wiremock) but FAZ 29 could not execute a full Compose-backed failure matrix on this host.

## Scenario coverage

| Failure mode | Expected control | Evidence status |
|--------------|------------------|-----------------|
| Publisher crash after DB commit | Transactional outbox relay | InMemory outbox + FAZ 28 messaging scenario tests (when suite compiles) |
| Consumer crash mid-handler | Inbox idempotency / at-least-once | Scenario tests in shared-kernel `faz28` |
| Poison message | DLQ | Stores/topology helpers; not Compose-proven |
| Queue backlog growth | `QueueDepthGuard` | Unit scenario exists; not wired to all work queues |
| Worker restart redelivery | Inbox dedupe | Covered in messaging resilience scenario |
| Graph token / API failure | Retry + controlled egress | WireMock integration tests under microsoft-connection |
| LLM timeout / connection fail | Provider protocol tests + local mock | Unit level |
| Delivery duplicate send | Idempotency key + approval gate | Domain modeled; InMemory risk remains |
| Dual-publish extraction cutover | Feature flag dual-publish | Transcript extraction `@ConditionalOnProperty` stubs only |
| Node / broker loss | RabbitMQ HA + replay | **Not tested** (Docker daemon missing) |

## What was run in FAZ 29

| Command / probe | Outcome |
|-----------------|---------|
| `./scripts/test-all` | **Failed** (Java reactor) — distributed scenario classes not fully executed in that run |
| Docker Compose E2E chaos | **Not run** (`DOCKER_DAEMON_MISSING`) |
| `DailyMeetingLoadScenarioTest` | Present (in-memory load); not confirmed green in failed reactor |
| Observability clean test | Pass after `clean test` (PII redaction) |

## Gaps blocking a “passed” failure report

1. No Testcontainers / Compose chaos suite for broker kill, DB kill, MinIO kill.  
2. InMemory backbone cannot validate Rabbit DLX/TTL behavior.  
3. Delivery confirmation vs acceptance not proven against real Graph mail.  
4. Extraction dual-publish soak absent.

## Conclusion

Treat distributed reliability as **design-complete / proof-incomplete**. Re-run this report after green CI with Compose failure injects and durable outbox/inbox adapters.
