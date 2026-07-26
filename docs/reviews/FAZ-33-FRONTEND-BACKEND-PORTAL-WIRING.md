# FAZ-33 Frontend ↔ Backend Portal Wiring

**Phase:** FAZ 33  
**Date:** 2026-07-26  
**Status:** Complete (portal BFF + mock-auth HTTP client + local seed)

## 1. Faz özeti

Web portal varsayılan olarak in-memory mock API kullanıyordu; HTTP modunda auth header yoktu ve OpenAPI portal DTO’ları domain controller şekilleriyle çakışıyordu (`/api/v1/me` → `UserView`, `/api/v1/meetings` → `MeetingResponse`). Bu turda FE↔BE uçtan uca bağlandı:

- Portal BFF: `/api/v1/portal/*` (portal OpenAPI şekilleri)
- FE HTTP client: mock Entra header’ları + portal path’leri
- Local seed: mock tid → tenant + demo meetings
- `run-local` / `.env.example`: `VITE_API_MODE=http` varsayılanı

## 2. Akış

```text
web-portal (VITE_API_MODE=http)
  + X-Mock-Entra-Oid/Tid/… headers
        ↓
GET /api/v1/portal/me          → PortalUser (permission string’leri map’li)
GET /api/v1/portal/dashboard   → aggregates + recent meetings
GET /api/v1/portal/meetings    → MeetingSummary page
GET /api/v1/portal/meetings/{id} → MeetingDetail (participants + ledger slices)
        ↓
MeetingApi / ContinuityLedgerApi / IdentityApi (domain façades)
```

Local boot:

```text
PortalLocalSeedRunner (local profile)
  → TenantApi.provision(entraTid=local-dev-tid)
  → seed 2 demo meetings
```

## 3. Değişenler

| Alan | Dosya |
|------|--------|
| Portal BFF | `security/portal/PortalApiController`, `PortalPermissionMapper` |
| Local seed | `PortalLocalSeedRunner` + `application-local.yml` |
| FE HTTP | `apps/web-portal/src/api/client.ts` (`mockAuthHeaders`, `/portal` paths) |
| Env / run | `.env.example`, `scripts/run-local`, `vite-env.d.ts` |
| Contract | `platform-api.yaml` portal paths |
| Test | `PortalApiBindingTest` |

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| FE HTTP mode + mock auth headers | ✓ |
| Portal `/me` PortalUser shape | ✓ |
| Portal meetings list/detail against real MeetingApi | ✓ |
| Local tenant seed matching mock tid | ✓ |
| `run-local` wires VITE_* for HTTP | ✓ |
| Transcript segments by meeting | deferred (empty payload) |
| Note update via portal path | deferred (points to `/meeting-notes`) |
| Full notes/approvals/actions UI data | deferred (needs MI seed) |
| Entra MSAL | deferred |
| OpenAPI codegen | deferred |

## 5. Testler

```bash
export JAVA_HOME=$PWD/.tools/jdk-21
./mvnw -pl apps/platform-backend -am test \
  -Dtest='PortalApiBindingTest' \
  -Dsurefire.failIfNoSpecifiedTests=false

pnpm --filter @actenora/web-portal test
```

Smoke (manuel):

```bash
# backend local profile + seed
curl -s -H 'X-Mock-Entra-Oid: local-oid-admin' \
     -H 'X-Mock-Entra-Tid: local-dev-tid' \
     -H 'X-Mock-Global-Admin: true' \
     http://127.0.0.1:8080/api/v1/portal/me
```

## 6. Bilinen riskler

- Domain `/api/v1/me` ve `/api/v1/meetings` hâlâ native şekilleri döner; portal yalnızca `/api/v1/portal/*` kullanır.
- Transcript / notes / actions portal yüzeyleri iskelet; üç panelli UI boş segment/note ile açılır.
- Seed InMemory; process restart’ta yeniden oluşur (JDBC gelene kadar kabul edilebilir).
