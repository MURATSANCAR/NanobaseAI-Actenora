# BOUNDED-CONTEXTS

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## Target bounded contexts

| ID | Context | Mission | Primary language of change |
|----|---------|---------|----------------------------|
| `identity` | Identity & Access | Tenants, users, roles, API keys | Auth events, principal |
| `workspace` | Workspace | Workspaces, membership, settings | Workspace lifecycle |
| `evidence` | Evidence | Intake, classification, evidence index | EvidenceRegistered |
| `artifact` | Artifact Store | Blob metadata & storage keys (not bytes in DB) | ArtifactStored |
| `knowledge` | Knowledge | Entities, links, graph projections over evidence | KnowledgeLinked |
| `case` | Case | Business case / ticket binding for workflows | CaseOpened |
| `planning` | Planning | AI plan proposals grounded in evidence | PlanProposed |
| `modelgw` | Model Gateway | Multi-model routing, inference jobs, quotas | InferenceCompleted |
| `prompt` | Prompt Registry | Versioned prompts & output schemas | PromptPublished |
| `workflow` | Workflow | Long-running orchestrations & timers | WorkflowTransitioned |
| `approval` | Approval | Human gates before external effects | ApprovalGranted / Denied |
| `execution` | Execution | Run approved action steps | StepExecuted |
| `delivery` | Delivery | External adapters (side-effecting) | DeliveryAttempted |
| `notify` | Notification | In-app / email / webhook notifications | NotificationSent |
| `audit` | Audit | Immutable audit & decision log | AuditAppended |
| `platform` | Platform / Shared Kernel | IDs, clocks, errors, money/time types only | — |

## Context map (integration style)

```text
identity ──provides──▶ all contexts (principal)
workspace ──scopes──▶ evidence, case, workflow, approval

evidence ──feeds──▶ knowledge, planning, audit
artifact ──stores──▶ evidence blobs

planning ──uses──▶ modelgw, prompt, knowledge, evidence
workflow ──orchestrates──▶ planning, approval, execution, delivery
approval ──gates──▶ delivery
execution ──invokes──▶ delivery adapters (only if approved)
delivery ──emits──▶ notify, audit

modelgw ──isolates──▶ local LLM runtime
audit ◀──appends── all mutating contexts (async)
```

## Relationship styles

| From → To | Style |
|-----------|-------|
| workflow → approval | Domain events + commands (async) |
| approval → delivery | Explicit command after grant |
| planning → modelgw | Application service port (sync or async job) |
| * → audit | Outbox events only |
| * → identity | Read principal; no user table ownership elsewhere |
| Any → another’s tables | **Forbidden** (ADR-002, ADR-009, ADR-012) |

## Module naming (target)

```
com.nanobaseai.actenora.<context>.{domain,application,infrastructure,api}
```

Shared kernel: `com.nanobaseai.actenora.sharedkernel` — types & interfaces only; **no** business services.

## MVP subset (Phase 1 recommendation)

Minimum vertical slice: `identity`, `workspace`, `evidence`, `artifact`, `planning`, `modelgw`, `prompt`, `workflow`, `approval`, `delivery`, `audit`.

Defer full `knowledge` graph sophistication and rich `notify` channels if needed; keep stubs with contracts.
