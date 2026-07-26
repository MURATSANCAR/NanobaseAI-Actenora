# Graph Sandbox Runbook

Operator runbook for end-to-end Microsoft Graph integration against a real Entra tenant and Graph app registration. **No demo seed data** — provision tenant, business context, and subscriptions explicitly.

## Prerequisites

- PostgreSQL, RabbitMQ, MinIO (or compatible object storage) running locally or in sandbox infra
- Microsoft Entra app registration with application permissions for Calendar.Read, OnlineMeetings.Read, OnlineMeetingTranscript.Read.All (as required by your Graph subscription resources)
- Public HTTPS webhook URL (ngrok, Azure Front Door, etc.) reachable by Microsoft Graph

## Environment

Copy `.env.example` and set:

| Variable | Purpose |
|----------|---------|
| `SPRING_PROFILES_ACTIVE` | `graph-sandbox,local` |
| `ACTENORA_MICROSOFT_GRAPH_ENABLED` | `true` |
| `ACTENORA_MICROSOFT_GRAPH_CLIENT_STATE` | Shared secret matching subscription `clientState` |
| `ACTENORA_MICROSOFT_GRAPH_TENANT_ID` / `CLIENT_ID` / `CLIENT_SECRET` | Preferred Graph credentials (`AUTH_MODE=CLIENT_SECRET` for sandbox) |
| `MICROSOFT_TENANT_ID` / `MICROSOFT_CLIENT_ID` / `MICROSOFT_CLIENT_SECRET` | Legacy aliases still accepted |
| `ACTENORA_MICROSOFT_GRAPH_DEFAULT_MAILBOX_USER_ID` | UPN or Graph user id for transcript/calendar API calls |
| `POSTGRES_*`, `RABBITMQ_*`, `OBJECT_STORAGE_*` | JDBC + messaging + transcript storage |

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

Lifecycle notifications (`reauthorizationRequired`, `missed`) trigger automatic renewal via `SubscriptionLifecycleService`.

## Happy path

1. Graph change notification → calendar delta sync → `CalendarMeetingUpsertAdapter` creates/updates `MeetingOccurrence`
2. `meeting.MeetingOccurrenceUpserted.v1` → transcript known-meeting store + transcript poll enqueue (if meeting ended)
3. `TeamsTranscriptPollScheduler` / scheduled fallback → Graph transcript download → `TranscriptApi.ingestFromGraphVtt` (idempotent on Graph transcript id)
4. `transcript.TranscriptReady.v1` → `TranscriptReadyAiAdmissionHandler` submits CHUNK_EXTRACTION AI job
5. Portal `GET /api/v1/portal/meetings/{id}` and `/transcript` compose real notes/segments when modules are available

## Acceptance script

```bash
./scripts/acceptance-graph-sandbox.sh
```

## Staging burn-in (1 real Teams meeting)

Ops-only; code path exists. Complete before Gate 11 sign-off:

1. Entra Graph app + **CERTIFICATE** auth + admin consent for calendar/meeting/transcript permissions
2. HTTPS public webhook URL registered; `clientState` matches `ACTENORA_MICROSOFT_GRAPH_CLIENT_STATE`
3. Mailbox user + tenant **transcript policy** allow Graph transcript read
4. Actenora tenant mapped to Entra `tid`; business context created
5. Schedule/run **one** real Teams meeting with transcript enabled
6. Confirm chain: webhook → calendar upsert → transcript ingest → AI draft → portal approve
7. Portal login via MSAL Bearer (see [`PORTAL-MSAL-RUNBOOK.md`](PORTAL-MSAL-RUNBOOK.md)) — no `X-Actenora-*`

## Troubleshooting

| Symptom | Check |
|---------|--------|
| Webhook 401/rejected | `clientState` mismatch vs subscription |
| Tenant unresolved in logs | Entra tid not mapped in `tenant.tenants.entra_tenant_id` |
| `GRAPH_BUSINESS_CONTEXT_REQUIRED` | Create business context via Meeting API |
| Empty portal transcript | Meeting ended + Teams transcript available + default mailbox user configured |
| Duplicate meeting on replay | Expected — upsert catches `DuplicateGraphIdentityException` and updates |
