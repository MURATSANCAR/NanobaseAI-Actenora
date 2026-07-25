# DISTRIBUTED-WORKFLOWS

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## Canonical meeting-intelligence path

```text
Microsoft subscription / poll
  → MeetingOccurrenceUpserted
  → TranscriptAvailable / TranscriptIngested
  → AiProcessingRequested
  → InferenceCompleted (local models via model-management)
  → MeetingInsightProposed  (evidence-bound)
  → ApprovalRequested
  → ApprovalGranted
  → DeliveryRequested (template-rendered)
  → DeliverySucceeded
  → AuditAppended
```

`ApprovalDenied` or exhausted failures → no external delivery; notify operators.

## Reliability

| Concern | Pattern |
|---------|---------|
| Dual-write | Transactional outbox per schema (ADR-004) |
| Consume | Inbox idempotency |
| Poison | RabbitMQ DLQ |
| Long AI | Async jobs; platform-backend + ai-orchestrator |

## Sync vs async

| Interaction | Mode |
|-------------|------|
| Operator approve/reject | Sync command → async fan-out |
| Graph webhook | Sync accept → async process |
| Inference | Async |
| SMTP delivery | Async; gated by approval id |
