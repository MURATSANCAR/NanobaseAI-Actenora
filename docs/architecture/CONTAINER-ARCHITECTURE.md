# CONTAINER-ARCHITECTURE

**Status:** Locked for Phase 0  
**Date:** 2026-07-25  
**Compose:** `infrastructure/compose/docker-compose.yml`

## Containers

| Container | Responsibility | Tech |
|-----------|----------------|------|
| `platform-backend` | Modular monolith API + module host | Java 21 / Spring Boot 3.4 |
| `ai-orchestrator` | AI worker / orchestration HTTP | Python 3.12 / FastAPI |
| `web-portal` | Operator UI | React / Vite |
| `teams-meeting-app` | Teams surface | Node / TypeScript |
| `postgres` | System of record | PostgreSQL 16 |
| `rabbitmq` | Commands / events / DLQ | RabbitMQ 3.13 |
| `redis` | Cache / short-lived coordination | Redis 7 |
| `minio` | Object storage | MinIO |
| `mailhog` | Local SMTP sink | MailHog |
| `otel-collector` | Telemetry | OTel |
| Local LLM runtime | Inference (host or future compose service) | Ollama/vLLM/… |

## Diagram

```text
┌ web-portal ┐   ┌ teams-meeting-app ┐
└─────┬──────┘   └─────────┬─────────┘
      └──────────┬─────────┘
                 ▼
        ┌ platform-backend ┐◀──▶┌ ai-orchestrator ┐
        └────────┬─────────┘    └────────┬────────┘
     ┌───────┬───┴────┬──────────┐       │
     ▼       ▼        ▼          ▼       ▼
 postgres rabbitmq  redis     minio   llm-runtime
```

## Process notes

- Java BC modules ship inside `platform-backend` (Spring Modulith).
- `ai-orchestrator` is a separate process for Python-side model/worker concerns; contracts via HTTP/events.
- Reserved future services (not containers yet): see `repo-map.yaml` `reserved_services`.
