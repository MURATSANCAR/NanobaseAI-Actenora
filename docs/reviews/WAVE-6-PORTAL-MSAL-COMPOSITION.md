# Wave 6 — Portal MSAL / composition foundations

## Scope

Portal BFF composition with explicit stub signaling, note update via Meeting Intelligence, MSAL auth (fail-closed SPA), and HTTP mutation gating.

## Portal BFF composition

| Endpoint | Source | Stub header |
|----------|--------|-------------|
| `/portal/me`, `/portal/meetings`, `/portal/dashboard` | `IdentityApi`, `MeetingApi`, `ContinuityLedgerApi` | — |
| `/portal/commitments`, `/portal/decisions` | Ledger projections | — |
| `/portal/operations/overview` | `OperationsApi.queueDashboard` when bean present | `X-Actenora-Composition: stub` when absent |
| `/portal/meetings/{id}/transcript` | Transcript composition when modules present | stub when absent |
| `/portal/templates`, `/portal/ai-jobs`, `/portal/audit/events` | No list façade yet | stub |
| `/portal/teams/settings`, `/portal/model-control/health` | Teams/model wiring deferred | stub |
| `PUT /portal/meetings/{id}/notes/{noteId}` | `MeetingIntelligenceApi.updateNote` when bean present | 501 ProblemDetails when absent |

Stub responses set header:

```
X-Actenora-Composition: stub
```

## Note update

When `MeetingIntelligenceApi` is on the classpath:

```
PUT /api/v1/portal/meetings/{meetingId}/notes/{noteId}
{ "body": "..." }
→ MeetingIntelligenceApi.updateNote(noteId, MeetingNoteUpdateRequest)
```

Returns 501 `NOTE_UPDATE_UNAVAILABLE` when intelligence module bean is missing.

## Portal auth mode

```yaml
actenora:
  portal:
    auth:
      mode: mock   # local: X-Mock-* headers
      # mode: msal  # prod: Entra Bearer from SPA
  security:
    auth:
      mode: entra  # required when portal.auth.mode=msal
```

Production (`application-prod.yml`):

- `actenora.portal.auth.mode=msal`
- `actenora.security.auth.mode=entra`
- Spring OAuth2 resource server JWT (`ACTENORA_ENTRA_ISSUER_URI`, `ACTENORA_ENTRA_AUDIENCE`)
- `PortalAuthModeGuard` enforces MSAL+Entra on strict prod; MSAL without Entra refused everywhere

## Frontend MSAL integration

Implemented in `apps/web-portal`:

- `MsalAuthProvider` + `AuthGate` (misconfig screen when Entra vars missing)
- `buildMsalConfig` / `msalApiScopes` fail-closed (`MsalConfigError`)
- `resolveAuthHeaders()` sends Bearer only in msal mode — never `X-Mock-*`
- Dockerfile refuses msal builds without `VITE_ENTRA_CLIENT_ID` + `VITE_ENTRA_API_SCOPE`

Operator steps: [`docs/operations/PORTAL-MSAL-RUNBOOK.md`](../operations/PORTAL-MSAL-RUNBOOK.md).

Local dev remains:

```
VITE_API_MODE=http
VITE_PORTAL_AUTH_MODE=mock
```

with env-supplied `mockAuthHeaders()` (no baked demo personas).

## HTTP mutations flag

`portalMutationsEnabled("http", "msal"|"mock")` → `true` for note save / approval / actions.

## Tests

```bash
mvn -pl apps/platform-backend test -Dtest=PortalApiBindingTest,PortalAuthModeGuardTest,MockAuthProductionGuardTest
pnpm --filter @actenora/web-portal test
```

## Exit criteria

- [x] Stub endpoints emit `X-Actenora-Composition: stub`
- [x] Note update via `MeetingIntelligenceApi` when available
- [x] `actenora.portal.auth.mode` + prod defaults
- [x] MSAL SPA provider + fail-closed config + Bearer wiring
- [x] HTTP mutations enabled for msal and mock local auth
- [ ] Live Entra tenant login proof (ops — runbook)

## Deferred

- Template list façade
- Audit timeline list on portal BFF
- Full Wave 6 “nice-to-have” MSAL polish tracked separately from Gate 11 critical path
