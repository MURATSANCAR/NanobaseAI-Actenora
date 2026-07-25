# FAZ 2 — Local Infrastructure Baseline

**Status:** Implemented  
**Date:** 2026-07-25

## Acceptance

```bash
cp .env.example .env
docker compose -f infrastructure/compose/docker-compose.yml --env-file .env up -d
./scripts/verify-faz2
```

| Check | Expected |
|-------|----------|
| Infra containers | healthy |
| Backend liveness | UP |
| Backend readiness | UP (db + rabbit + redis) |
| AI liveness | UP |
| AI readiness (no Qwen) | DEGRADED |
| MinIO | buckets + `tenants/tenant-local-test/` seeded |
| RabbitMQ | `actenora.domain` + queues from definitions |
| MailHog | ≥1 test message |

## Layout

- `infrastructure/compose/docker-compose.yml` — networks, volumes, health/readiness `depends_on`
- `infrastructure/postgres/init/` — extensions + schema-per-BC
- `infrastructure/rabbitmq/definitions.json` — declarative topology
- `infrastructure/redis/redis.conf` — persistence optional via `REDIS_PERSISTENCE`
- `infrastructure/minio/init-buckets.sh` — buckets + tenant prefix
- `infrastructure/otel/otel-collector-config.yaml`
- `infrastructure/mailhog/send-test-mail.py`
- `docs/operations/TENANT-BUCKET-PREFIX.md`
- `docs/operations/LOCAL-DEVELOPMENT.md`
