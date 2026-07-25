# INITIAL-GAP-ANALYSIS

**Phase:** 0 — Repository baseline, gap analysis, ADR lock  
**Date:** 2026-07-25  
**Evidence:** `artifacts/phase-0/mvn-package-full.txt`, `mvn-test-full.txt`, `*.exit`, `baseline-capture.txt`

## 1. Executive verdict

Actenora is a **polyglot modular-monolith monorepo** for **Teams meeting intelligence**, not an empty repo. Architecture ADRs and product docs are locked. **Maven package and test are currently RED** (shared-kernel compile/interface drift under active development). Python orchestrator health tests pass. Qwen is hard-coded in `ai-processing` routing/adapters — remediation planned under ADR-006.

Phase 0 documentation work did **not** add end-to-end business features; it locks decisions and documents the real scaffold gap. Concurrent scaffolding outside this phase already contains substantial domain islands (`meeting`, `ai-processing`, `model-management`, messaging backbone).

## 2. Repository baseline

| Item | State |
|------|-------|
| Build | Maven wrapper (`./mvnw`), Java 21, Spring Boot 3.4 |
| Modules | 14 BCs + `shared-kernel` under `modules/` |
| Apps | `platform-backend`, `ai-orchestrator`, `web-portal`, `teams-meeting-app` |
| Infra | Compose: Postgres, RabbitMQ, Redis, MinIO, MailHog, OTel |
| Contracts | `packages/event-contracts`, `api-contracts`, observability, test-support |
| Registry | `repo-map.yaml` |
| Commands | `make bootstrap\|build\|test\|run\|stop` |

### Stack detection

| Tech | Present |
|------|---------|
| Java / Spring / Modulith | Yes |
| JPA `@Entity` production usage | Not found (POJOs + Flyway SQL) |
| Python / FastAPI | Yes (`ai-orchestrator`) |
| Node / pnpm | Yes (portal, teams app, contracts) |
| Docker Compose | Yes |
| PostgreSQL schemas via Flyway | Yes (per module) |
| RabbitMQ | Compose + Spring config; ports in shared-kernel |
| MinIO | Compose + object-storage port |

### Config / secrets

| Method | State |
|--------|-------|
| `.env.example` | Present (no real secrets) |
| `.env` | Gitignored local |
| Spring `application-*.yml` | Present on platform-backend |
| Vault | Not wired |

## 3. Build & test (real results)

| Command | Exit | Primary failure |
|---------|------|-----------------|
| `./mvnw -DskipTests package` | **1** | `shared-kernel` — `TransientFailureOutboxStore` does not implement `OutboxStore.findByStatus`; `countByStatus` return type `int` vs `long` |
| `./mvnw test` | **1** | Fails compiling/running `shared-kernel` tests (`EventBackboneTest`, resilience scenarios) amid interface/class drift |
| `uv run pytest` (ai-orchestrator) | **0** | 3 passed |
| Host `mvn`/`docker` without bootstrap | Missing on bare host; `.tools` JDK used for capture |

Full logs: `artifacts/phase-0/mvn-package-full.txt`, `artifacts/phase-0/mvn-test-full.txt`.

**Do not treat CI as green until package exit 0.**

## 4. Test classification (approx.)

| Class | Examples | Notes |
|-------|----------|-------|
| Unit | Continuity, routing, observability, test-support | Mixed depth |
| Integration | Messaging resilience scenarios | Currently blocked by compile |
| Architecture | `ModularMonolithArchUnitTest`, `ModulithArchitectureTest` | Present under platform-backend |
| E2E | Not established | — |
| Load | Not present | — |
| App smoke | portal/teams/orchestrator health | Thin |

~49 test files discovered (Java/Python/TS), excluding `.tools`.

## 5. Module dependency / smell scan

| Smell | Finding |
|-------|---------|
| Direct cross-module DB access | Not observed (no JPA repos wiring across modules yet) |
| Shared JPA entities | None found |
| Cyclic Maven deps | Not proven; Modulith `allowedDependencies` declared on modules |
| Hard-coded models | **Yes — Qwen** (below) |
| Hexagonal purity | Domain mostly Spring-free; module `package-info` uses Modulith annotations; module POMs pull optional JPA |
| Schema clash | Postgres init previously listed draft schemas; **corrected** to Flyway module schemas |

## 6. Qwen hard-coded inventory

| Location | Kind |
|----------|------|
| `ModelRole.QWEN27_FINAL` | Domain enum vendor leak |
| `ValidationModelPreference.QWEN27_FINAL` | Domain preference |
| `TenantRoutingPolicy` / `TaskRoleMapping` / `MultiModelRoutingService` | Defaults & filters on Qwen role |
| `DefaultModelRoleBootstrap` (`QWEN27_*` keys/ids, deployment names) | Infra bootstrap constants |
| `Qwen27BModelAdapter` (`qwen2.5-32b-instruct`) | Adapter served model id |
| `LocalProviderModelRuntimeAdapter.qwen27B(...)` | Factory naming |
| Compose `QWEN_BASE_URL` | Env coupling |
| Tests asserting Qwen keys/adapters | Unit coupling |

**Remediation path:** M3 in `docs/ai/MULTI-MODEL-ARCHITECTURE.md` — rename roles to capability-oriented ids; keep physical model strings only in `modelmanagement` catalog / adapters.

## 7. Target vs current gap

| Locked target | Current | Gap |
|---------------|---------|-----|
| Modular monolith | Scaffold present | Stabilize compile; finish thin BCs |
| Schema-per-BC | Flyway schemas exist | Complete tables beyond messaging shells |
| Outbox/inbox | Ports + SQL stubs | Fix store implementations; Rabbit adapter |
| Object storage port | Port present | MinIO adapter implementation |
| Multi-model routing | Partial + Qwen hard-codes | Catalog-driven roles |
| Approval before delivery | Module + `approval.requests` | Wire insight → approval → delivery E2E |
| Evidence-first AI | ADR locked | Validators on insight propose |
| Extractable workers | Reserved paths in repo-map | Playbook only |

## 8. Bounded contexts & ownership

Locked in:

- `docs/architecture/BOUNDED-CONTEXTS.md`
- `docs/architecture/DATA-OWNERSHIP.md` (every known Flyway table assigned)

## 9. Service extraction candidates

From `repo-map.yaml` / `SERVICE-DECOMPOSITION-MAP.md`:

1. `services/model-worker` (P1)
2. `services/delivery-worker` (P1)
3. `services/transcript-worker` (P2)
4. `services/teams-integration-service` (P2)
5. `services/document-renderer` (P3)

## 10. Multi-model transition plan

See `docs/ai/MULTI-MODEL-ARCHITECTURE.md` § transition (M0–M5). **M0 done**; M1 tables exist; M3 (de-Qwen domain roles) is the next architecture-critical code step after build is green.

## 11. Transition plan (no reckless deletes)

1. Fix `shared-kernel` outbox store / interface drift → green `./mvnw package`.
2. Green `./mvnw test` + ArchUnit/Modulith.
3. Replace `QWEN27_FINAL` domain role with catalog roles.
4. Implement Rabbit + MinIO adapters behind ports.
5. Vertical slice: meeting → transcript → AI → approval → delivery (MailHog).
6. Extract workers only per playbook.

## 12. Acceptance criteria

| Criterion | Met? |
|-----------|------|
| Build/test documented with real output | **Yes** (exit 1 + logs) |
| Target BC list clear | **Yes** |
| Every known table has owner | **Yes** |
| Service extraction candidates set | **Yes** |
| Qwen hard-codes listed | **Yes** |
| Multi-model transition plan | **Yes** |
| Phase 0 docs/ADR set complete | **Yes** |
| Repo not left worse by Phase 0 docs | **Yes** (docs + schema init alignment); note: concurrent code may still be mid-red |

## 13. Risks

| Risk | Mitigation |
|------|------------|
| Parallel agents leave tree mid-compile | Single-thread green builds before merge |
| Qwen coupling spreads | ArchUnit ban on `QWEN` in `..domain..` |
| Delivery without approval | Enforce in delivery module + Arch/integration tests |
| Graph credential sprawl | Keep in microsoft-connection; extract service later |
