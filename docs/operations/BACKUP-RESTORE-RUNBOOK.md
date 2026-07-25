# BACKUP-RESTORE-RUNBOOK

**Status:** Active (FAZ 27)  
**Audience:** Platform operators / on-call

## Scope

Backup and restore for Actenora production data planes:

| Component | Data class | Tooling |
|-----------|------------|---------|
| PostgreSQL | System of record (all BC schemas) | `pg_dump` / PITR |
| MinIO / S3 | Transcripts & evidence objects | Bucket versioning + replication |
| RabbitMQ | Transient — not primary backup | Quorum queues; rebuild from outbox |
| Redis | Ephemeral cache | No restore required |

## Backup schedule (recommended)

| Asset | Cadence | Retention |
|-------|---------|-----------|
| Postgres full dump | Daily 02:00 UTC | 35 days |
| Postgres WAL / PITR | Continuous | 7–14 days |
| Object storage | Continuous versioning | Align with tenant retention policy |
| Secrets (Vault / KMS refs) | On change | Immutable versions |

## Backup procedure (Postgres)

```bash
# Example — replace connection from sealed secret / Vault
pg_dump \
  --format=custom \
  --no-owner \
  --file="actenora-$(date -u +%Y%m%dT%H%M%SZ).dump" \
  "$POSTGRES_JDBC_URL"
```

Encrypt artifacts before leaving the VPC (`age` / KMS envelope). Store in a separate account/region.

## Restore procedure (Postgres)

1. Declare incident; freeze retention jobs (`actenora.security.retention` / ops toggle).
2. Provision empty Postgres; apply Flyway baselines if required.
3. `pg_restore --clean --if-exists -d "$TARGET_DB" actenora-YYYYMMDD.dump`
4. Verify row counts for `tenant`, `transcript`, `audit.entries`, `operations.legal_holds`.
5. Re-point platform-backend; run smoke: `/actuator/health/readiness`.
6. Resume retention jobs only after legal-hold inventory check.

## Object storage restore

1. Identify tenant prefix `tenants/{tenantId}/…`.
2. Restore from versioned object or replica bucket.
3. Confirm content-hash metadata matches transcript rows.
4. Re-issue download authorizations only after integrity check.

## Verification checklist

- [ ] Health readiness green
- [ ] Sample tenant transcript download authorization succeeds
- [ ] Audit append still works (append-only; no UPDATE/DELETE)
- [ ] Legal holds still present for held resources
- [ ] AI egress still denies public LLM hosts
