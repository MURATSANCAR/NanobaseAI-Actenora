# DISTRIBUTED-WORKFLOWS

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## 1. Decision

Long-running work is **event-driven choreography** coordinated by the **Workflow** bounded context, with durable state and timers — not synchronous call chains and not XA transactions (ADR-003).

## 2. Canonical happy path

```text
EvidenceRegistered
  → (optional) KnowledgeLinked
  → PlanRequested
  → InferenceCompleted / PlanProposed
  → ApprovalRequested
  → ApprovalGranted
  → ExecutionStarted
  → DeliveryRequested
  → DeliverySucceeded
  → WorkflowCompleted
```

Any `ApprovalDenied` or terminal failure → compensating notifications + audit; **no** delivery.

## 3. Workflow instance model (logical)

| Field | Meaning |
|-------|---------|
| `workflow_id` | Durable id |
| `definition_id` + version | Which state machine |
| `correlation_id` | End-to-end trace |
| `state` | Current state (see STATE-MACHINES) |
| `context_refs` | Opaque ids to case/evidence/plan |
| `timers` | Durable wakeups |

## 4. Reliability pattern

| Concern | Pattern |
|---------|---------|
| Publish after commit | Transactional outbox (ADR-004) |
| At-least-once consume | Inbox idempotency (ADR-004) |
| Poison messages | DLQ + operator replay |
| Partial delivery failure | Retry with backoff; never skip approval |
| Clock / delay | Broker TTL or workflow timers — not thread.sleep in API |

## 5. Sync vs async

| Interaction | Mode |
|-------------|------|
| Operator HTTP commands | Sync accept → async process |
| Model inference | Async job via modelgw (may offer sync for tiny prompts in lab only) |
| External delivery | Async; status via events |
| Approval decision | Sync command from UI; async fan-out |

## 6. Failure domains

- Model timeout → plan stays `PROPOSING`; retry or fail workflow per policy.
- Approval timeout → escalate / expire per Approval policy; never auto-deliver.
- Delivery adapter down → retry; workflow `DELIVERING` until success/exhaustion.
