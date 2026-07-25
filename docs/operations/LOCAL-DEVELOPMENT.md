# LOCAL-DEVELOPMENT

**Status:** Phase 1 bootstrap  
**Date:** 2026-07-25

## Prerequisites

- macOS/Linux (Windows: Git Bash/WSL or `scripts/*.ps1`)
- Optional Docker for Postgres / RabbitMQ / MinIO
- Or run `./scripts/bootstrap` which installs JDK 21, Node 22 + pnpm, uv, syft under `.tools/`

## Quick start

```bash
./scripts/bootstrap   # toolchains + locked deps; copies .env.example → .env once
./scripts/build-all
./scripts/test-all
./scripts/lint-all
./scripts/run-local
./scripts/stop-local
```

Make equivalents: `make bootstrap|build|test|lint|run|stop|sbom|ci-build|ci-test`.

### Windows

```powershell
.\scripts\bootstrap.ps1
.\scripts\build-all.ps1
.\scripts\test-all.ps1
.\scripts\lint-all.ps1
.\scripts\run-local.ps1
.\scripts\stop-local.ps1
```

## Package managers

| Stack | Tool | Lock |
|-------|------|------|
| Java | Maven Wrapper (`./mvnw`) | reactor + local `.m2` |
| Python | uv | `uv.lock` |
| Node | pnpm only | `pnpm-lock.yaml` |

## Independent builds

```bash
./mvnw -pl apps/platform-backend -am -Dmaven.test.skip=true package
uv sync --package actenora-orchestrator
pnpm --filter @actenora/web-portal build
pnpm --filter @actenora/teams-meeting-app build
```

## Local infra

```bash
docker compose -f infrastructure/compose/docker-compose.yml up -d
```

`./scripts/run-local` starts compose when Docker is available, then process-mode apps.

## Secrets

Never commit `.env`, keystores, or PEMs. Use `.env.example` as the template.

## SBOM

```bash
./scripts/generate-sbom
# → artifacts/sbom/*.cdx.json
```
