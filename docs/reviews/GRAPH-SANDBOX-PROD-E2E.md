# Graph Sandbox Prod E2E — Implementation Review

Milestones B–F wire real Microsoft Graph calendar/transcript flows into Actenora without mock business datasets.

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
- `scripts/acceptance-graph-sandbox.sh`
- Subscription admin REST `/api/v1/microsoft/subscriptions`
- Lifecycle webhook renew on reauth/missed
- Full `actenora.microsoft-graph.*` bindings in `application-prod.yml` + `.env.example`

## Explicit non-goals

- No `PortalLocalSeedRunner` or canned meetings/users
- No demo tenant seed runners

## Verification

```bash
./mvnw -pl apps/platform-backend -am -DskipTests compile
./scripts/acceptance-graph-sandbox.sh   # against running graph-sandbox profile
```
