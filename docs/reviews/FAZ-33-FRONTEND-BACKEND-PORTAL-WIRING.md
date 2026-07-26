# FAZ-33 Frontend ↔ Backend Portal Wiring

**Phase:** FAZ 33  
**Date:** 2026-07-26  
**Status:** Complete (portal BFF + operator identity HTTP client). **Local demo seed removed.**

## 1. Faz özeti

Web portal varsayılan olarak in-memory mock API kullanıyordu; HTTP modunda auth header yoktu ve OpenAPI portal DTO’ları domain controller şekilleriyle çakışıyordu (`/api/v1/me` → `UserView`, `/api/v1/meetings` → `MeetingResponse`). Bu turda FE↔BE uçtan uca bağlandı:

- Portal BFF: `/api/v1/portal/*` (portal OpenAPI şekilleri)
- FE HTTP client: operator-supplied Entra identity headers (real oid/tid/email — no canned personas) + portal path’leri
- Meetings/notes come from Graph ingest or admin APIs — **no** `PortalLocalSeedRunner` / demo meetings
- `run-local` / `.env.example`: `VITE_API_MODE=http`; identity env vars required (fail-closed)

## 2. Akış

```text
web-portal (VITE_API_MODE=http)
  + X-Actenora-Entra-Oid/Tid/… headers (real operator identity from env)
        ↓
GET /api/v1/portal/me          → PortalUser (permission string’leri map’li)
GET /api/v1/portal/dashboard   → aggregates + recent meetings
GET /api/v1/portal/meetings    → MeetingSummary page
GET /api/v1/portal/meetings/{id} → MeetingDetail (participants + ledger slices)
        ↓
MeetingApi / ContinuityLedgerApi / IdentityApi (domain façades)
```

Tenant bootstrap: provision via Tenant API with the real Entra directory id — never seed demo meetings.

## 3. Değişenler

| Alan | Dosya |
|------|--------|
| Portal BFF | `security/portal/PortalApiController`, `PortalPermissionMapper` |
| FE HTTP | `apps/web-portal/src/api/client.ts` (`mockAuthHeaders`, `/portal` paths) |
| Env / run | `.env.example`, `scripts/run-local` (requires real `VITE_IDENTITY_*`), `vite-env.d.ts` |
| Contract | `platform-api.yaml` portal paths |
| Test | `PortalApiBindingTest` |
| Removed | `PortalLocalSeedRunner`, canned ops `seededDemo`, script persona fallbacks |

## 4. Kabul matrisi

See original acceptance checklist in git history; identity headers must be operator-supplied real Entra values.
