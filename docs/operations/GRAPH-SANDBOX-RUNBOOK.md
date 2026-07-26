# Graph Sandbox Runbook

Operator runbook for end-to-end Microsoft Graph integration against a real Entra tenant and Graph app registration. **No demo seed data** — provision tenant, business context, and subscriptions explicitly.

## Prerequisites

- PostgreSQL, RabbitMQ, MinIO (or compatible object storage) running locally or in sandbox infra
- Microsoft Entra app registration with application permissions for Calendar.Read, OnlineMeetings.Read, OnlineMeetingTranscript.Read.All (as required by your Graph subscription resources)
- Public HTTPS webhook URL (ngrok, Azure Front Door, etc.) reachable by Microsoft Graph
- `actenora.persistence.mode=jdbc` and `actenora.messaging.mode=jdbc-rabbit`. Production boot fails closed when Graph is enabled without either durable mode, certificate auth, or certificate/key paths.

## Environment

Copy `.env.example` and set:

| Variable | Purpose |
|----------|---------|
| `SPRING_PROFILES_ACTIVE` | `graph-sandbox,local` |
| `ACTENORA_MICROSOFT_GRAPH_ENABLED` | `true` |
| `ACTENORA_MICROSOFT_GRAPH_CLIENT_STATE` | Bootstrap/default secret; runtime validates the stored `clientState` for each subscription |
| `ACTENORA_MICROSOFT_GRAPH_TENANT_ID` / `CLIENT_ID` / `CLIENT_SECRET` | Preferred Graph credentials (`AUTH_MODE=CLIENT_SECRET` for sandbox) |
| `MICROSOFT_TENANT_ID` / `MICROSOFT_CLIENT_ID` / `MICROSOFT_CLIENT_SECRET` | Legacy aliases still accepted |
| `ACTENORA_MICROSOFT_GRAPH_DEFAULT_MAILBOX_USER_ID` | UPN or Graph user id for transcript/calendar API calls |
| `POSTGRES_*`, `RABBITMQ_*`, `OBJECT_STORAGE_*` | JDBC + messaging + transcript storage |
| `ACTENORA_MICROSOFT_GRAPH_RECONCILE_INTERVAL` | Subscription/calendar reconciliation cadence (default `PT30M`) |
| `ACTENORA_MICROSOFT_GRAPH_TRANSCRIPT_POLL_MAX_ATTEMPTS` / `MAX_AGE` | Durable transcript retry bounds (defaults `24` / `PT48H`) |

Start platform-backend:

```bash
./mvnw -pl apps/platform-backend -am spring-boot:run -Dspring-boot.run.profiles=graph-sandbox,local
```

## One-time tenant setup (no seeds)

1. **Provision Actenora tenant** — bind Entra tid to Actenora tenant via Tenant API / admin tooling so `TenantApi.findByEntraTenantId` resolves webhook `tenantId`.
2. **Create business context** (required before calendar upsert):

   ```http
   POST /api/v1/meetings/business-contexts
   Authorization: Bearer …
   { "name": "Default", "description": "Graph sandbox" }
   ```

   Without this, calendar sync throws `GRAPH_BUSINESS_CONTEXT_REQUIRED`.

## Graph subscriptions

Create a mailbox calendar subscription (authenticated as tenant admin):

```http
POST /api/v1/microsoft/subscriptions
{
  "resource": "users/{mailbox-upn}/events",
  "changeType": "created,updated,deleted",
  "notificationUrl": "https://{public-host}/api/v1/microsoft/webhooks/graph-notifications",
  "lifecycleNotificationUrl": "https://{public-host}/api/v1/microsoft/webhooks/graph-notifications",
  "clientState": "{ACTENORA_MICROSOFT_GRAPH_CLIENT_STATE}",
  "expirationWindow": "PT48H"
}
```

List / renew:

- `GET /api/v1/microsoft/subscriptions`
- `POST /api/v1/microsoft/subscriptions/renew-expiring`

Lifecycle notifications (`reauthorizationRequired`, `missed`) trigger automatic renewal via `SubscriptionLifecycleService`. A distributed-lease-protected scheduled reconciliation also renews expiring subscriptions and polls subscribed mailboxes every 30 minutes by default. This heals missed lifecycle/change notifications and prevents concurrent work across platform replicas.

## Happy path

1. Graph change notification → atomic notification claim + durable `microsoft.GraphChangeNotificationReceived.v1` outbox enqueue → immediate `202`
2. `GraphChangeWorkConsumer` → calendar delta sync → `CalendarMeetingUpsertAdapter` creates/updates `MeetingOccurrence`; transient failures retry through the event backbone and exhausted/permanent failures enter DLQ
3. `meeting.MeetingOccurrenceUpserted.v1` → durable transcript poll work enqueue (if meeting ended)
4. `TeamsTranscriptPollScheduler` claims DB work with `SKIP LOCKED` → Graph transcript download → `TranscriptApi.ingestFromGraphVtt` (idempotent on Graph transcript id); unavailable transcripts retry with bounded exponential backoff
5. `transcript.TranscriptReady.v1` → `TranscriptReadyAiAdmissionHandler` submits CHUNK_EXTRACTION AI job
6. Portal `GET /api/v1/portal/meetings/{id}` and `/transcript` compose real notes/segments when modules are available

## Monitoring and alerts

Scrape `/actuator/prometheus` from a private scrape network (endpoint is explicitly `permitAll` for Prometheus; do not expose it publicly). Alert on:

- `actenora_graph_webhook_notifications_total{outcome="rejected"}` increasing
- `actenora_graph_tenant_unmapped_total` increasing
- `actenora_graph_subscriptions_expiring > 0`
- `actenora_graph_subscription_lifecycle_total{event="subscriptionRemoved"}` increasing (recreate the affected subscription)
- `actenora_graph_subscription_renew_total{outcome="failure"}` increasing
- `actenora_graph_transcript_oldest_pending_seconds` above the agreed transcript SLO
- `actenora_graph_http_requests_total{outcome=~"5xx|transport"}` or sustained `status="429"` increases
- `actenora_graph_circuit_open == 1`
- `actenora_messaging_dlq_depth > 0`

The portal Operations page reports the Microsoft Graph circuit state and includes pending transcript work in queue depth. The Teams settings page reports recent webhook rejection as `degraded`.

## Acceptance script

```bash
./scripts/acceptance-graph-sandbox.sh
```

## Staging burn-in (1 real Teams meeting)

Ops-only; code path exists. Complete before Gate 11 sign-off:

1. Entra Graph app + **CERTIFICATE** auth + admin consent for calendar/meeting/transcript permissions
2. HTTPS public webhook and lifecycle URLs registered; Graph validation completes in under 10 seconds
3. Subscription-specific `clientState` stored by Actenora matches the notification value (unknown subscriptions fail closed in production)
4. Mailbox uses a stable Entra object id or UPN; tenant **transcript policy** allows Graph transcript read
5. Actenora tenant mapped to Entra `tid`; business context created
6. Subscription expiry is beyond the configured renew threshold; scheduled reconciliation succeeds
7. Schedule/run **one** real Teams meeting with transcript enabled
8. Confirm chain: webhook → durable Graph work → calendar upsert → durable transcript poll → transcript ingest → AI draft → portal approve
9. Confirm Graph circuit closed, no open DLQ items, and transcript pending age returns to zero
10. Portal login via MSAL Bearer (see [`PORTAL-MSAL-RUNBOOK.md`](PORTAL-MSAL-RUNBOOK.md)) — no `X-Actenora-*`

## Troubleshooting

| Symptom | Check |
|---------|--------|
| Webhook rejected | Unknown subscription id or subscription-specific `clientState` mismatch |
| Tenant unresolved in logs | Entra tid not mapped in `tenant.tenants.entra_tenant_id` |
| `GRAPH_BUSINESS_CONTEXT_REQUIRED` | Create business context via Meeting API |
| Empty portal transcript | Meeting ended + Teams transcript available + default mailbox user configured |
| Transcript poll in `DEAD_LETTER` | Fix mailbox/permission/policy configuration, then `POST /api/v1/microsoft/subscriptions/transcript-polls/{meetingOccurrenceId}/requeue` |
| Graph circuit `OPEN` | Inspect Graph 429/5xx/transport metrics; it half-opens automatically after the configured duration |
| Duplicate meeting on replay | Expected — upsert catches `DuplicateGraphIdentityException` and updates |
