# SYSTEM-CONTEXT

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## 1. System under design

**Actenora** — NanobaseAI governed action & workflow platform (modular monolith).

## 2. Context diagram (C4 Level 1)

```text
                         ┌─────────────────────┐
                         │  Operators / Owners │
                         │  Auditors           │
                         └──────────┬──────────┘
                                    │ HTTPS
                                    ▼
┌──────────────┐          ┌─────────────────────┐          ┌──────────────────┐
│ Evidence     │─────────▶│                     │─────────▶│ External systems │
│ sources      │  ingest  │     ACTENORA        │ approved │ (ITSM, ERP, CRM, │
│ (files, API, │          │  modular monolith   │ delivery │  mail, webhooks) │
│  DB export)  │          │                     │          └──────────────────┘
└──────────────┘          └──────────┬──────────┘
                                     │
              ┌──────────────────────┼──────────────────────┐
              ▼                      ▼                      ▼
     ┌────────────────┐    ┌─────────────────┐    ┌─────────────────┐
     │ PostgreSQL     │    │ RabbitMQ        │    │ Object storage  │
     │ (schema/BC)    │    │ (events/cmds)   │    │ (MinIO-compat)  │
     └────────────────┘    └─────────────────┘    └─────────────────┘
                                     │
                                     ▼
                           ┌─────────────────┐
                           │ Local LLM runtime│
                           │ (Ollama/vLLM/…)  │
                           └─────────────────┘
```

## 3. Actors

| Actor | Intent |
|-------|--------|
| Operator | Review proposals, approve/reject, monitor workflows |
| Domain owner | Configure policies, adapters, approval matrices |
| Platform engineer | Operate infra, models, queues, backups |
| Auditor | Read-only audit & evidence trail |
| External system | Receives **approved** deliveries only |
| Evidence source | Supplies artifacts / payloads for intake |

## 4. Trust boundaries

| Boundary | Rule |
|----------|------|
| Public / operator UI → API | Authenticated; least privilege |
| Actenora → Local LLM | Private network; no PII logging of raw prompts in default prod |
| Actenora → External systems | Only after Approval context grants delivery |
| Actenora → Object storage | Server-side credentials; signed URLs time-boxed |
| Schema boundaries | No cross-context SQL joins for writes; read via published views/APIs only if explicitly allowed |

## 5. Sibling systems (not runtime dependencies)

- **NanobaseAI-QA** — may supply conformance/evidence packs in future integrations; not required for Actenora core.
- **NanobaseAI-BI** — analytics sibling; Actenora does not embed Query Gateway.

## 6. Deployment unit (initial)

One Actenora application process (+ optional worker processes sharing the same codebase modules), plus Postgres, RabbitMQ, MinIO, local LLM runtime. See `CONTAINER-ARCHITECTURE.md`.
