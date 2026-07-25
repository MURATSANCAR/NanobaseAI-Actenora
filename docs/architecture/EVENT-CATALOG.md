# EVENT-CATALOG

**Status:** Locked for Phase 0 (v0 contracts)  
**Date:** 2026-07-25  
**Envelope version:** `actenora.event.envelope.v1`

## 1. Envelope

```json
{
  "eventId": "uuid",
  "eventType": "evidence.EvidenceRegistered.v1",
  "eventVersion": 1,
  "occurredAt": "ISO-8601",
  "correlationId": "uuid",
  "causationId": "uuid",
  "tenantId": "string",
  "workspaceId": "string",
  "producer": "evidence",
  "payload": {}
}
```

## 2. Naming

`<context>.<EventName>.v<major>`

Breaking payload changes → new major. Additive optional fields → same major with consumer tolerance.

## 3. Catalog (initial)

| Event type | Producer | Consumers (typical) | Notes |
|------------|----------|---------------------|-------|
| `evidence.EvidenceRegistered.v1` | evidence | knowledge, workflow, audit | New evidence available |
| `evidence.EvidenceClassified.v1` | evidence | planning, audit | Classification ready |
| `artifact.ArtifactStored.v1` | artifact | evidence, audit | Blob pointer committed |
| `knowledge.KnowledgeLinked.v1` | knowledge | planning, audit | Graph link created |
| `case.CaseOpened.v1` | case | workflow, audit | Case created |
| `planning.PlanRequested.v1` | workflow/planning | modelgw, planning | Start plan |
| `planning.PlanProposed.v1` | planning | approval, workflow, audit | Requires evidence bindings |
| `modelgw.InferenceCompleted.v1` | modelgw | planning, audit | Includes model id used |
| `modelgw.InferenceFailed.v1` | modelgw | planning, workflow, audit | Retry/fail policy |
| `prompt.PromptPublished.v1` | prompt | modelgw, planning | New immutable version |
| `workflow.WorkflowTransitioned.v1` | workflow | notify, audit | State change |
| `workflow.WorkflowCompleted.v1` | workflow | notify, audit | Terminal success |
| `workflow.WorkflowFailed.v1` | workflow | notify, audit | Terminal failure |
| `approval.ApprovalRequested.v1` | approval | notify, audit | Human gate |
| `approval.ApprovalGranted.v1` | approval | execution, delivery, workflow, audit | Unlocks delivery |
| `approval.ApprovalDenied.v1` | approval | workflow, notify, audit | Blocks delivery |
| `execution.ExecutionStarted.v1` | execution | workflow, audit | |
| `execution.StepExecuted.v1` | execution | workflow, audit | |
| `delivery.DeliveryRequested.v1` | delivery | audit | Must cite approval id |
| `delivery.DeliverySucceeded.v1` | delivery | workflow, notify, audit | |
| `delivery.DeliveryFailed.v1` | delivery | workflow, notify, audit | |
| `notify.NotificationSent.v1` | notify | audit | |
| `audit.AuditAppended.v1` | audit | (export sinks) | Optional fan-out |

## 4. Mandatory fields for AI / delivery events

| Event family | Required payload fields |
|--------------|-------------------------|
| `PlanProposed` | `planId`, `planVersion`, `evidenceIds[]`, `promptVersionId`, `modelDecisionId` |
| `ApprovalGranted` | `approvalId`, `planId`, `planVersion`, `decidedBy` |
| `DeliveryRequested` | `deliveryOrderId`, `approvalId`, `adapterType`, `payloadRef` |

Missing evidence on `PlanProposed` → reject at producer validation (ADR-011).

## 5. Transport

- RabbitMQ exchanges/queues per context; routing keys = event type.
- Published only via transactional outbox.
