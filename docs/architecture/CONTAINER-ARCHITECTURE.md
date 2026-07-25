# CONTAINER-ARCHITECTURE

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## 1. C4 containers (target)

| Container | Responsibility | Tech (target) |
|-----------|----------------|---------------|
| `actenora-api` | HTTP/API façade, authn/z entry | Spring Boot |
| `actenora-worker` | Message consumers, workflow steps, model jobs | Same codebase, worker profile |
| `postgres` | System of record; schema per BC | PostgreSQL 16+ |
| `rabbitmq` | Commands, domain events, delayed retries | RabbitMQ 3.13+ |
| `object-store` | Evidence blobs, render artifacts | MinIO-compatible |
| `llm-runtime` | Local model serving | Ollama / vLLM / llama.cpp server |
| `actenora-web` (later) | Operator console | TBD (not Phase 0) |

Phase 0 ships **no** Compose/Helm; this document locks the intended topology.

## 2. Container diagram

```text
┌──────────────────────────────────────────────────────────────┐
│ Actenora host / cluster                                      │
│  ┌─────────────┐   ┌──────────────┐   ┌──────────────────┐   │
│  │ actenora-api│   │actenora-worker│   │ actenora-web     │   │
│  │ :8080       │   │ consumers     │   │ (future)         │   │
│  └──────┬──────┘   └──────┬───────┘   └──────────────────┘   │
│         │                 │                                    │
│         └────────┬────────┘                                    │
│                  │ modular monolith modules                    │
│                  ▼                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐  │
│  │ postgres │  │ rabbitmq │  │ minio    │  │ llm-runtime  │  │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

## 3. Process model

- **API** and **Worker** share module JARs; differ by Spring profile (`api` vs `worker`).
- Workers scale horizontally for queue depth; API scales for request concurrency.
- Domain logic lives in modules, not in container-specific code.

## 4. Network rules (intent)

| From | To | Allowed |
|------|----|---------|
| web / clients | api | HTTPS only |
| api / worker | postgres | private |
| api / worker | rabbitmq | private AMQP/TLS |
| api / worker | object-store | private S3 API |
| api / worker | llm-runtime | private HTTP |
| llm-runtime | internet model hubs | **ops-controlled pulls only**; inference stays local |
| api / worker | external SaaS LLM | **denied** by default (ADR-005) |

## 5. Health & readiness

Each container exposes health endpoints; worker readiness includes broker connectivity and DB migrator completion for owned schemas.

## 6. What is not a container yet

Individual bounded contexts are **modules**, not containers, until extraction criteria in `SERVICE-EXTRACTION-PLAYBOOK.md` are met.
