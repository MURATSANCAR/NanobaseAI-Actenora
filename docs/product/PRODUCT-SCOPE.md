# PRODUCT-SCOPE — NanobaseAI Actenora

**Status:** Locked for Phase 0  
**Date:** 2026-07-25  
**Product family:** NanobaseAI

## 1. One-line definition

Actenora is a **local-first AI action and workflow platform** that turns evidence into approved, auditable actions against enterprise systems — without sending model traffic to external LLM clouds.

## 2. Problem

Enterprises need AI that can:

1. Ingest heterogeneous evidence (documents, APIs, DB snapshots, tickets, mail).
2. Reason over that evidence with controllable local models.
3. Plan multi-step, long-running work.
4. **Never** mutate external systems until a human approves.
5. Prove every AI claim with evidence references.
6. Scale from a single deployable to extractable services without a rewrite.

Sibling products cover QA automation (`NanobaseAI-QA`) and BI/NL2SQL (`NanobaseAI-BI`). Actenora covers **action orchestration with governance**.

## 3. In-scope capabilities

| Capability | Description |
|------------|-------------|
| Evidence intake | Register, store, classify, and index evidence artifacts |
| Knowledge binding | Attach evidence to entities, cases, and workflow context |
| Multi-model reasoning | Route tasks to local models by capability policy |
| Workflow orchestration | Long-running, event-driven state machines |
| Human approval | Mandatory gate before external delivery / side effects |
| Action execution | Execute approved plans via adapters (email, ticket, ERP, webhook, …) |
| Audit & replay | Immutable decision/audit trail with correlation IDs |
| Prompt governance | Versioned prompts with schema-validated outputs |
| Modular deploy | Single modular monolith; extract hot contexts later |

## 4. Primary users

| Persona | Need |
|---------|------|
| Operator / analyst | Review evidence, approve or reject proposed actions |
| Domain owner | Define policies, approval rules, delivery adapters |
| Platform engineer | Operate local models, queues, storage, schemas |
| Auditor / security | Trace why an action was taken and which evidence supported it |

## 5. Success metrics (product)

- **Zero unapproved external side effects** in production paths.
- **Evidence coverage**: every AI-generated claim carries evidence IDs or is rejected.
- **Local LLM compliance**: no outbound calls to hosted LLM APIs in default profiles.
- **Extractability**: hot modules can be cut out without shared JPA entities or cross-schema writes.

## 6. Relationship to NanobaseAI family

```
NanobaseAI-QA  → test generation & conformance
NanobaseAI-BI  → governed analytics / NL2SQL
Actenora       → governed actions & long-running AI workflows
```

Shared principles (not shared runtime): local-first AI, evidence discipline, human review for high-impact outputs, explicit architecture locks via ADR.

## 7. Delivery posture

| Phase | Outcome |
|-------|---------|
| 0 (this) | Scope, architecture, ADR lock; no business code |
| 1+ | Modular monolith skeleton, bounded contexts, contracts |
| Later | Selective service extraction per playbook |

## 8. Open product questions (non-blocking for Phase 0)

- First vertical adapter pack (ITSM vs ERP vs CRM) for MVP.
- Tenant model depth (single-tenant appliance vs multi-tenant SaaS).
- UI surface ownership (embedded console vs portal card under `portal.nanobase.ai`).

These do not unblock architecture lock; they refine Phase 1+ backlog.
