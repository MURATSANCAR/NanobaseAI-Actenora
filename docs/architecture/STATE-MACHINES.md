# STATE-MACHINES

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## 1. Meeting occurrence (logical)

```text
[*] → SCHEDULED → IN_PROGRESS → COMPLETED
COMPLETED → TRANSCRIPT_PENDING → TRANSCRIPT_READY
TRANSCRIPT_READY → AI_PROCESSING → INSIGHT_READY
INSIGHT_READY → AWAITING_APPROVAL
AWAITING_APPROVAL → APPROVED → DELIVERING → CLOSED
AWAITING_APPROVAL → REJECTED → CLOSED
DELIVERING → DELIVERY_FAILED → DELIVERING | CLOSED
```

## 2. Transcript

```text
[*] → FETCHED → NORMALIZING → READY
FETCHED|NORMALIZING → FAILED
```

## 3. Approval request

```text
[*] → PENDING → GRANTED | DENIED | EXPIRED
```

## 4. Delivery order

```text
[*] → READY → IN_FLIGHT → SUCCEEDED
IN_FLIGHT → FAILED → READY | DEAD
```

**Invariant:** `READY` requires bound `approvalId` for the insight/document version.

## 5. AI processing job

```text
[*] → QUEUED → RUNNING → SUCCEEDED | FAILED
FAILED → QUEUED (retry) | DEAD
```
