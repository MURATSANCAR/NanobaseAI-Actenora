# Graph Sandbox Prod E2E — Implementation Review

Milestones A–F close the real Microsoft Graph → meeting note loop without mock business datasets.

## A — Durable Graph edge

- JDBC `JdbcSubscriptionStore` / `JdbcNotificationInbox` / `JdbcCalendarSyncCursorStore` (`persistence.mode=jdbc` + Graph enabled)
- Subscription admin REST `/api/v1/microsoft/subscriptions` (create/list/renew)
- Lifecycle renew on `reauthorizationRequired` / `missed`
- Claim idempotency tests + webhook clientState negatives

## B — Calendar → Meeting upsert

- `MeetingOccurrenceRepository.findByTenantIdAndGraphEventImmutableId` (JDBC + InMemory)
- `MeetingApi.findByGraphEventImmutableId`
- `CalendarMeetingUpsertAdapter` maps `CalendarEvent` → create/update; resolves tenant via `GraphTenantResolver` + `TenantApi`
- `GraphChangeNotificationProcessor` syncs calendar then upserts non-cancelled events

## C — Teams transcript poller

- `TranscriptIngestionService.ingestFromGraphVtt` with `TranscriptSource.TEAMS_GRAPH` + external transcript id idempotency
- `TeamsTranscriptIngestService` + `TeamsTranscriptPollScheduler` (event-driven on `MeetingOccurrenceUpserted` + `@Scheduled` fallback)
- Gated by `actenora.microsoft-graph.enabled` + `workers-enabled`

## D — TranscriptReady → AI

- `TranscriptReadyAiAdmissionHandler` consumes `transcript.TranscriptReady.v1`
- Wired in InMemory fan-out and JDBC/Rabbit transcript queue listener via `EventBackboneConsumerDispatch`

## E — Portal composition

- `PortalApiController.meetingDetail` loads notes + approval history from `MeetingIntelligenceApi` / `ApprovalApi`
- `/transcript` loads real segments via `TranscriptApi.listSegmentsForMeeting` (no stub header when data exists)

## F — Proof artifacts

- `docs/operations/GRAPH-SANDBOX-RUNBOOK.md`
- `application-graph-sandbox.yml`
- `scripts/acceptance-graph-sandbox.sh` (fails closed if Graph disabled)
- Full `ACTENORA_MICROSOFT_GRAPH_*` bindings in `application-prod.yml` / `application.yml` / `.env.example`

## Explicit non-goals

- No `PortalLocalSeedRunner` or canned meetings/users
- No demo tenant seed runners
- `prod-fixture` keeps Graph disabled for CI isolation

## Verification

```bash
./mvnw -pl apps/platform-backend -am -DskipTests compile
./scripts/acceptance-graph-sandbox.sh   # against running graph-sandbox profile
```

Hardened after gap pass: tenant unmapped throws `GRAPH_TENANT_UNMAPPED`; JoinWebUrl resolves onlineMeeting id; AI worker polls `executeNext`; sandbox AI defaults non-mock; cross-tenant meeting reads return 403.