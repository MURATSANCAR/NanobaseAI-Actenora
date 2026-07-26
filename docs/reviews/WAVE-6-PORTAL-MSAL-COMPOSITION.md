# Wave 6 — Portal MSAL / composition foundations

## Scope

Portal BFF composition with explicit stub signaling, note update via Meeting Intelligence, MSAL auth scaffold, and HTTP mutation gating.

## Portal BFF composition

| Endpoint | Source | Stub header |
|----------|--------|-------------|
| `/portal/me`, `/portal/meetings`, `/portal/dashboard` | `IdentityApi`, `MeetingApi`, `ContinuityLedgerApi` | — |
| `/portal/commitments`, `/portal/decisions` | Ledger projections | — |
| `/portal/operations/overview` | `OperationsApi.queueDashboard` when bean present | `X-Actenora-Composition: stub` when absent |
| `/portal/meetings/{id}/transcript` | Deferred transcript index | stub |
| `/portal/templates`, `/portal/ai-jobs`, `/portal/audit/events` | No list façade yet | stub |
| `/portal/teams/settings`, `/portal/model-control/health` | Teams/model wiring deferred | stub |
| `PUT /portal/meetings/{id}/notes/{noteId}` | `MeetingIntelligenceApi.updateNote` when bean present | 501 ProblemDetails when absent |

Stub responses set header:

```
X-Actenora-Composition: stub
```

Frontend can detect partial wiring without silent empty payloads.

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

## Frontend MSAL integration (scaffold)

1. Set env for HTTP mode against platform backend:
   ```
   VITE_API_MODE=http
   VITE_API_BASE_URL=https://api.example.com
   VITE_PORTAL_AUTH_MODE=msal
   ```
2. Install `@azure/msal-browser` (or existing Nanobase family MSAL wrapper).
3. Acquire token: `const token = await msalInstance.acquireTokenSilent({ scopes: ["api://<audience>/.default"] })`
4. Send `Authorization: Bearer ${token.accessToken}` on portal BFF calls — **do not** send `X-Mock-*` headers when `VITE_PORTAL_AUTH_MODE=msal`.
5. Enable write UI: `portalMutationsEnabled("http", "msal")` returns `true`.

Local dev remains:

```
VITE_API_MODE=http
VITE_PORTAL_AUTH_MODE=mock
```

with `mockAuthHeaders()` on each request.

## HTTP mutations flag

`apps/web-portal/src/api/client.ts`:

```typescript
portalMutationsEnabled(mode, portalAuthMode)
// mock API mode → always true
// http + mock|msal → true (BFF mutations wired)
```

Used by `MeetingCenterPanel` for note save, approval decide, action complete.

## Tests

```bash
mvn -pl apps/platform-backend test -Dtest=PortalApiBindingTest
pnpm --filter @actenora/web-portal test
```

## Exit criteria

- [x] Stub endpoints emit `X-Actenora-Composition: stub`
- [x] Note update via `MeetingIntelligenceApi` when available
- [x] `actenora.portal.auth.mode` scaffold + prod defaults
- [x] MSAL Bearer requirement documented
- [x] HTTP mutations enabled for msal and mock local auth

## Deferred

- Full transcript segment composition on portal BFF
- Template list façade
- Audit timeline list on portal BFF
- MSAL provider implementation in web-portal (env scaffold only)
