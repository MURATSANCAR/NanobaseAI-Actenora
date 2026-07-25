# PRODUCT-SCOPE — NanobaseAI Actenora

**Status:** Locked for Phase 0 (aligned to monorepo scaffold)  
**Date:** 2026-07-25  
**Product family:** NanobaseAI

## 1. One-line definition

Actenora is a **local-first Microsoft Teams meeting intelligence platform**: it connects to Microsoft 365, ingests meeting transcripts, runs multi-model local AI over evidence, produces structured meeting notes/insights, and **delivers externally only after human approval**.

## 2. Problem

Enterprises record many meetings but struggle to:

1. Keep a durable, tenant-scoped model of meetings, series, and continuity.
2. Ingest transcripts reliably from Microsoft Graph / Teams.
3. Produce grounded AI notes without cloud LLM data egress.
4. Prevent unreviewed AI content from being emailed or posted externally.
5. Evolve from a modular monolith to extractable workers without a rewrite.

## 3. In-scope capabilities

| Capability | Owning context (module) |
|------------|-------------------------|
| Identity & access | `identity` |
| Tenancy | `tenant` |
| Policy / retention / routing rules | `policy` |
| Microsoft 365 connection & Graph ports | `microsoft-connection` |
| Meetings, series, occurrences, relations | `meeting` |
| Transcript ingest & normalization | `transcript` |
| Local model catalog & deployments | `model-management` |
| AI jobs, routing, inference orchestration | `ai-processing` |
| Meeting insights / briefs aggregation | `meeting-intelligence` |
| Human approval gates | `approval` |
| Document / note templates | `template` |
| External delivery (mail, etc.) | `delivery` |
| Audit trail | `audit` |
| Ops / Flyway aggregation | `operations` |
| Shared IDs, outbox ports, object-storage port | `shared-kernel` |

### Applications

| App | Role |
|-----|------|
| `apps/platform-backend` | Spring Boot modular monolith host |
| `apps/ai-orchestrator` | Python FastAPI AI worker/orchestrator |
| `apps/web-portal` | Operator web UI (Vite/React) |
| `apps/teams-meeting-app` | Teams-facing Node app |

## 4. Primary users

| Persona | Need |
|---------|------|
| Meeting participant / organizer | Accurate notes grounded in transcript |
| Approver | Review AI output before external send |
| Tenant admin | Connections, policies, model routing |
| Platform engineer | Local models, queues, storage, schemas |
| Auditor | Who approved what, on which evidence |

## 5. Success metrics

- Zero unapproved external deliveries in production paths.
- AI factual claims cite transcript/evidence ids (or fail closed).
- Local-only LLM default (ADR-005).
- Schema-per-module ownership retained under load and extraction.

## 6. NanobaseAI family

```
NanobaseAI-QA  → test automation / conformance
NanobaseAI-BI  → governed analytics / NL2SQL
Actenora       → Teams meeting intelligence + governed delivery
```

## 7. Phase posture

| Phase | Outcome |
|-------|---------|
| 0 | Scope, architecture, ADR lock; gap analysis of real scaffold |
| 1+ | Complete ports/adapters, fix build, wire end-to-end meeting → approval → delivery |
