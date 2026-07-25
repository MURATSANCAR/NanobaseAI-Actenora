# LOCAL-DEVELOPMENT

**Status:** Locked for Phase 0 (target guide)  
**Date:** 2026-07-25

## Current reality

The repository is **greenfield**: no Gradle/Maven modules, no Compose file, no runnable app.

Captured on 2026-07-25 (see `artifacts/phase-0/baseline-capture.txt`):

| Tool | Host status |
|------|-------------|
| `mvn` / `gradle` | Missing |
| `npm` | Missing |
| `python3` | Present; `pytest` module missing |
| `uv` | Present |
| `docker` | Missing |
| `make` | Present |

Phase 1 will introduce the modular monolith toolchain. Until then, local “development” is **documentation and ADR work only**.

## Target local stack (Phase 1+)

| Component | Intent |
|-----------|--------|
| JDK 21+ | Build/run Spring modules |
| Gradle or Maven | Multi-module build (choice locked in Phase 1 ADR/bootstrap) |
| Docker Compose | postgres, rabbitmq, minio, llm-runtime |
| IDE | IntelliJ / VS Code with Java |

### Planned commands (not runnable yet)

```bash
# illustrative — will exist after Phase 1 bootstrap
./gradlew test
./gradlew :apps:api:bootRun
docker compose -f deploy/compose.yml up -d
```

## Environment variables (planned)

| Variable | Purpose |
|----------|---------|
| `ACTENORA_DB_URL` | JDBC URL |
| `ACTENORA_RABBITMQ_URL` | AMQP URL |
| `ACTENORA_OBJECT_STORE_ENDPOINT` | MinIO API |
| `ACTENORA_LLM_BASE_URL` | Local runtime OpenAI-compatible endpoint |
| `ACTENORA_PROFILE` | `api` / `worker` / `local` |

Secrets via `.env` (gitignored) or exported shell vars — never committed.

## Developer workflow (target)

1. Start infra containers.
2. Run Flyway/Liquibase per schema.
3. Boot API + worker.
4. Pull at least one local model into llm-runtime.
5. Run unit + ArchUnit tests before PR.

## Phase 0 allowed work

- Edit docs/ADR only.
- Do not add business feature modules yet.
