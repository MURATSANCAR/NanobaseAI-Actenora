# Transcript service extraction (FAZ 26)

**Status:** Simulation complete  
**Date:** 2026-07-25  
**Candidate:** `services/transcript-worker`

## Goal

Prove the Transcript bounded context can run as a separate Spring Boot deployable without
cross-schema queries or distributed transactions.

## Feature flag

| Property | Values | Meaning |
|----------|--------|---------|
| `actenora.transcript.mode` | `embedded` (default) | Transcript beans + controller run in-process |
| `actenora.transcript.mode` | `remote` | Platform disables embedded beans; HTTP proxy to worker |

Env aliases:

- `ACTENORA_TRANSCRIPT_MODE`
- `TRANSCRIPT_WORKER_BASE_URL` (when remote)
- Timeouts / retries: `actenora.transcript.remote.*`

## Boundaries proven

| Invariant | How |
|-----------|-----|
| Own schema | Worker Flyway locations = `classpath:db/migration/transcript` only |
| No meeting tables | ArchUnit + migration SQL scan; opaque `meetingOccurrenceId` |
| Meeting ID via contract | `meeting.MeetingOccurrenceUpserted.v1` payload + upload query param |
| Event / API | Outbox `transcript.TranscriptIngested.v1` + HTTP `/api/v1/transcripts` |
| Separate health | Worker `/api/health` + `/actuator/health/{liveness,readiness}` |
| Separate image | `services/transcript-worker/Dockerfile` |
| Timeout / retry | `TranscriptRemoteClient` connect/read timeout + max-retries |
| Duplicate event | Inbox idempotency (`IdempotentEventConsumer`) |
| Restart safety | Outbox row durable before broker publish |
| No XA | Local `transcript.outbox_event` only |

## Dual-publish cutover

1. Deploy `transcript-worker` (Compose profile `extraction`).
2. Set platform `ACTENORA_TRANSCRIPT_MODE=remote` and `TRANSCRIPT_WORKER_BASE_URL`.
3. Soak: inbox catches duplicates; outbox survives restarts.
4. After soak, remove embedded classpath dependency (future cut — not required for simulation).

## Rollback

See [rollback section](#rollback) below and `docs/reviews/FAZ-26-TRANSCRIPT-EXTRACTION-REPORT.md`.

### Rollback

1. Set `ACTENORA_TRANSCRIPT_MODE=embedded` on platform-backend and restart.
2. Pause / scale down `transcript-worker` publishers (stop the container).
3. Inbox idempotency prevents duplicate side effects if events are replayed during re-attach.
4. Do **not** run two writers against the same transcript rows; embedded mode must be exclusive.

```bash
# Embedded (default monolith)
export ACTENORA_TRANSCRIPT_MODE=embedded

# Extracted simulation
docker compose -f infrastructure/compose/docker-compose.yml --profile extraction up -d transcript-worker
export ACTENORA_TRANSCRIPT_MODE=remote
export TRANSCRIPT_WORKER_BASE_URL=http://localhost:8081
```

## Anti-patterns avoided

- No FK from `transcript.*` to `meeting.*`
- No shared DB user required for simulation (local uses same Postgres instance, separate schema)
- No chatty sync validation of meeting rows inside transcript
