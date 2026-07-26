# BACKUP-RESTORE-DRILL

**Owner:** Platform operators  
**Cadence:** Quarterly (minimum)  
**Primary runbook:** [`BACKUP-RESTORE-RUNBOOK.md`](BACKUP-RESTORE-RUNBOOK.md)

## Purpose

Prove RPO/RTO assumptions with a controlled restore into an isolated environment — not production.

## Pre-drill checklist

- [ ] Drill scheduled; incident commander assigned
- [ ] Isolated Postgres instance provisioned (no prod network route)
- [ ] Latest encrypted backup artifact identified (Postgres dump + WAL/PITR point)
- [ ] Object storage replica or versioned bucket access confirmed
- [ ] Flyway baseline version documented for restore target tag
- [ ] Legal-hold inventory exported (no retention job runs on clone)

## Drill steps

### 1. Postgres restore

- [ ] Restore dump to isolated DB: `pg_restore --clean --if-exists -d "$TARGET_DB" actenora-YYYYMMDD.dump`
- [ ] Verify schemas: `tenant`, `identity`, `transcript`, `audit`, `operations`
- [ ] Row-count spot check: tenants ≥ 1, audit entries append-only trigger present

### 2. Object storage restore

- [ ] Restore sample tenant prefix from versioned object
- [ ] Confirm content-hash matches transcript metadata row

### 3. Application smoke (fixture or staging)

- [ ] Point `platform-backend` at restored DB (read-only user first if available)
- [ ] `/actuator/health/readiness` green
- [ ] `/api/health` green
- [ ] Sample transcript download authorization succeeds
- [ ] Audit append test: new entry written; no UPDATE/DELETE on historical rows

### 4. Messaging / outbox (informational)

- [ ] RabbitMQ rebuilt empty (transient); outbox relay drains from restored `operations.outbox_events`
- [ ] DLQ depth zero at drill start; monitor during relay catch-up

## Post-drill

- [ ] Record actual RPO (backup timestamp vs drill start) and RTO (restore complete → smoke green)
- [ ] File gaps in [`BACKUP-RESTORE-RUNBOOK.md`](BACKUP-RESTORE-RUNBOOK.md) if steps failed
- [ ] Tear down isolated resources; secure-delete clone credentials

## Success criteria

| Metric | Target |
|--------|--------|
| RPO demonstrated | ≤ 15 min (PITR) or documented actual |
| RTO demonstrated | ≤ 4 h (per [`DISASTER-RECOVERY-RUNBOOK.md`](DISASTER-RECOVERY-RUNBOOK.md)) |
| Data integrity | Row counts + hash match + audit immutability |
| Smoke tests | All application checks green |

## Drill log template

```text
Date:
Participants:
Backup artifact ID:
Restore target:
RPO achieved:
RTO achieved:
Issues:
Follow-ups:
```
