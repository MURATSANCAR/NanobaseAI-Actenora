# Wave 7 — Prod-like compose proof

## Scope

Docker Compose stack that exercises **JDBC persistence**, **jdbc-rabbit messaging**, and **prod security guards** with the documented **`prod-fixture`** profile for automated acceptance (mock IdP, MailHog mail sink).

## Files

| Path | Purpose |
|------|---------|
| `infrastructure/compose/docker-compose.prod-like.yml` | Postgres, RabbitMQ, Redis, MinIO, MailHog, platform-backend; optional `portal` / `ai` profiles |
| `infrastructure/compose/.env.prod-fixture.example` | Non-default secrets + `SPRING_PROFILES_ACTIVE=prod,prod-fixture,it` |
| `apps/platform-backend/src/main/resources/application-prod-fixture.yml` | Mock auth, jdbc-rabbit, mailhog, local portal seed |
| `scripts/acceptance-compose.sh` | Health wait + `/api/health`, liveness, optional portal `/me` |

## Quick start

```bash
cp infrastructure/compose/.env.prod-fixture.example infrastructure/compose/.env.prod-fixture
docker compose -f infrastructure/compose/docker-compose.prod-like.yml \
  --env-file infrastructure/compose/.env.prod-fixture up -d --build
./scripts/acceptance-compose.sh
```

Optional services:

```bash
# + web-portal static UI
docker compose -f infrastructure/compose/docker-compose.prod-like.yml \
  --env-file infrastructure/compose/.env.prod-fixture --profile portal up -d --build

# + ai-orchestrator
docker compose -f infrastructure/compose/docker-compose.prod-like.yml \
  --env-file infrastructure/compose/.env.prod-fixture --profile ai up -d --build
```

## Profile matrix

| Profile combo | Auth | Persistence | Messaging | Mail | Use |
|---------------|------|-------------|-----------|------|-----|
| `prod,prod-fixture,it` | mock (`X-Mock-*`) | jdbc | jdbc-rabbit | mailhog | CI / acceptance (default env file) |
| `prod` + Entra env | entra JWT | jdbc | jdbc-rabbit | microsoft-graph | Real prod-like (manual secrets; no mock) |

`prod-fixture` is **never** enabled in real production clusters. It relaxes only:

- `actenora.security.auth.mode=mock` (via `ActenoraProfiles.isStrictProduction`)
- MailHog host check in `ProductionSecretGuard`
- Mock LLM provider kind

Secrets must still be non-default (`ProductionSecretGuard` unchanged for credential values).

## Acceptance script

`scripts/acceptance-compose.sh`:

1. Waits for `actuator/health/liveness` UP (default 300s)
2. Asserts `/api/health` and `/actuator/health/liveness`
3. When `prod-fixture` is active: `GET /api/v1/portal/me` with mock headers
4. When `web-portal` container is running (or `CHECK_PORTAL=true`): fetches portal index HTML
5. Exits non-zero on any failure

Environment overrides: `COMPOSE_FILE`, `ENV_FILE`, `BASE_URL`, `WAIT_TIMEOUT_SEC`, `CHECK_PORTAL`.

## Tests

```bash
mvn -pl apps/platform-backend test -Dtest=MockAuthProductionGuardTest,ProductionSecretGuardTest
./scripts/acceptance-compose.sh   # requires Docker
```

## Exit criteria

- [x] `docker-compose.prod-like.yml` with data plane + platform-backend
- [x] Non-default secrets via env file
- [x] `prod-fixture` profile documented and wired
- [x] `acceptance-compose.sh` with health + portal probe
- [x] Optional ai-orchestrator and web-portal profiles

## Deferred

- Full Graph webhook → calendar E2E in compose (requires Entra + ngrok)
- MSAL SPA E2E (Wave 6 scaffold; MSAL provider not in web-portal yet)
- Load/soak in compose (Wave 9 SLO doc)
