# MICROSERVICE-EXTRACTION-READINESS

**Phase:** FAZ 29  
**Date:** 2026-07-25  
**Related:** [`SERVICE-EXTRACTION-PLAYBOOK.md`](../architecture/SERVICE-EXTRACTION-PLAYBOOK.md), [`SERVICE-DECOMPOSITION-MAP.md`](../architecture/SERVICE-DECOMPOSITION-MAP.md), ADR-001

## Verdict

**Not ready for broad extraction.** Early dual-publish shells exist for two workers; playbook preconditions are not met (compile/test red, contracts not soak-proven, outbox not durable under load in monolith).

## Playbook gate checklist

| Gate | Status |
|------|--------|
| Measurable scale / isolation need | Partial motivation only (LLM GPU, delivery secrets) — no production metrics |
| Schema exclusive per BC | **Intent yes** (schema-per-context + Flyway locations) |
| No cross-module JPA / shared tables | **Mostly** (plain domain modules; ArchUnit present) — suite currently red |
| Events/commands stable v1+ with consumer contract tests | **Partial** (JSON schemas validated; consumer contract soak missing) |
| Outbox/inbox proven under load in monolith | **No** (InMemory stores dominate; FAZ 28 scenario is in-process) |
| On-call ownership named | **No** |

## Reserved / started extraction slots

| Path | Status | Notes |
|------|--------|-------|
| `services/teams-integration-service` | **Started shell** | Spring Boot app + Graph notification/reconciliation stubs |
| `services/transcript-worker` | **Started shell** | Health + Flyway config; not a full ingest worker |
| `services/model-worker` | Reserved empty | P1 candidate per decomposition map |
| `services/document-renderer` | Reserved empty | |
| `services/delivery-worker` | Reserved empty | Domain `DeliveryWorker` loop exists inside module JAR |

Platform dual-publish adapters: `apps/platform-backend/.../platform/extraction/transcript/*` (`@ConditionalOnProperty`).

## Extraction readiness by candidate

| Candidate | Ready? | Blockers |
|-----------|--------|----------|
| Model worker | **No** | InMemory routing/queue; orchestrator is health/egress only |
| Delivery worker | **No** | MailHog default provider; InMemory repo; no prod Graph mail binding proof |
| Transcript worker | **No** | Shell only; Graph fetch loop incomplete |
| Teams integration | **No** | Shell; Entra/Teams SSO product path incomplete |
| Document renderer | **No** | HTML/PDF pipeline incomplete |

## Recommendation

1. Stay modular monolith until `./scripts/test-all` is green and JDBC outbox/inbox replace InMemory defaults for hot paths.  
2. Keep extraction behind feature flags / dual-publish only.  
3. First real cut candidates remain **model-worker** and **delivery-worker** after durable messaging + secret isolation proofs.  
4. Follow [`SERVICE-EXTRACTION-RUNBOOK.md`](../operations/SERVICE-EXTRACTION-RUNBOOK.md) for cutover/rollback — do not invent ad-hoc splits.
