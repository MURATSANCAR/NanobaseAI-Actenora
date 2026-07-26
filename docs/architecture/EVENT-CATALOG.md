# EVENT-CATALOG

**Status:** Locked for Phase 0 (v0)  
**Date:** 2026-07-25  
**Envelope:** `actenora.event.envelope.v1`

## Envelope

```json
{
  "eventId": "uuid",
  "eventType": "meeting.MeetingOccurrenceUpserted.v1",
  "eventVersion": 1,
  "occurredAt": "ISO-8601",
  "correlationId": "uuid",
  "causationId": "uuid",
  "tenantId": "uuid",
  "producer": "meeting",
  "payload": {}
}
```

## Catalog (initial)

| Event type | Producer | Typical consumers |
|------------|----------|-------------------|
| `microsoftconnection.SubscriptionChanged.v1` | microsoft-connection | meeting, operations |
| `meeting.MeetingOccurrenceUpserted.v1` | meeting | transcript, meeting-intelligence, audit |
| `meeting.MeetingRelationSuggested.v1` | meeting | approval (optional), audit |
| `transcript.TranscriptIngested.v1` | transcript / transcript-worker | ai-processing, audit |
| `transcript.TranscriptReady.v1` | transcript / transcript-worker | ai-processing, meeting-intelligence |
| `aiprocessing.AiJobRequested.v1` | ai-processing / meeting-intelligence | ai-orchestrator, audit |
| `aiprocessing.AiJobCompleted.v1` | ai-processing | meeting-intelligence, audit |
| `aiprocessing.AiJobFailed.v1` | ai-processing | meeting-intelligence, operations, audit |
| `meetingintelligence.InsightProposed.v1` | meeting-intelligence | approval, audit |
| `meetingintelligence.NoteApprovedForLedger.v1` | meeting-intelligence | continuity-ledger (same process / MI consumer), audit |
| `approval.ApprovalRequested.v1` | approval | notify/UI, audit |
| `approval.ApprovalGranted.v1` | approval | delivery, audit |
| `approval.ApprovalDenied.v1` | approval | meeting-intelligence, audit |
| `delivery.DeliveryRequested.v1` | delivery | audit |
| `delivery.DeliverySucceeded.v1` | delivery | meeting-intelligence, audit |
| `delivery.DeliveryFailed.v1` | delivery | operations, audit |
| `modelmanagement.ModelCatalogChanged.v1` | model-management | ai-processing, audit |
| `audit.AuditAppended.v1` | audit | export sinks |

## Mandatory payload fields

| Event | Required |
|-------|----------|
| `InsightProposed` | `insightId`, `transcriptId`s / evidence refs, `promptVersion`/`modelDecisionId` when AI-derived |
| `ApprovalGranted` | `approvalId`, `insightId` (or deliverable id), `decidedBy` |
| `DeliveryRequested` | `deliveryOrderId`, `approvalId`, `adapterType` |

## Transport (FAZ 10)

- Publish **only** via transactional outbox (`outbox_event`).
- Relay via polling publisher (CDC-ready `EventTransport` port).
- Consume with inbox idempotency; poison → DLX/DLQ + `dead_letter_event`.
- Runtime design: [`EVENT-BACKBONE.md`](EVENT-BACKBONE.md).
