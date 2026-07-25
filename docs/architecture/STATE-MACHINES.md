# STATE-MACHINES

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## 1. Case workflow (core)

```text
[*] → INTAKE
INTAKE → EVIDENCE_READY
EVIDENCE_READY → PLANNING
PLANNING → AWAITING_APPROVAL
AWAITING_APPROVAL → APPROVED
AWAITING_APPROVAL → REJECTED
AWAITING_APPROVAL → EXPIRED
APPROVED → EXECUTING
EXECUTING → DELIVERING
DELIVERING → COMPLETED
DELIVERING → DELIVERY_FAILED
EXECUTING → EXECUTION_FAILED
REJECTED → CLOSED
EXPIRED → CLOSED
DELIVERY_FAILED → CLOSED | DELIVERING (retry policy)
EXECUTION_FAILED → CLOSED | PLANNING (replan policy)
COMPLETED → [*]
CLOSED → [*]
```

### Guards

| Transition | Guard |
|------------|-------|
| → DELIVERING | `ApprovalGranted` for this plan version |
| → COMPLETED | All required delivery orders terminal success |
| → PLANNING from failure | Policy allows replan; new plan version required |

## 2. Approval request

```text
[*] → PENDING
PENDING → GRANTED
PENDING → DENIED
PENDING → EXPIRED
GRANTED|DENIED|EXPIRED → [*]
```

## 3. Inference job (modelgw)

```text
[*] → QUEUED → RUNNING → SUCCEEDED
RUNNING → FAILED
QUEUED → CANCELLED
FAILED → QUEUED (retry) | DEAD
```

## 4. Delivery order

```text
[*] → READY
READY → IN_FLIGHT → SUCCEEDED
IN_FLIGHT → FAILED → READY (retry) | DEAD
READY → CANCELLED
```

**Invariant:** `READY` only created when approval decision id is bound.

## 5. Storage

State stored in owning BC tables (`workflow.instances`, `approval.requests`, …). Transitions append to `workflow.transitions` and emit domain events via outbox.
