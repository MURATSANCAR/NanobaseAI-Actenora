# DISASTER-RECOVERY-RUNBOOK

**Status:** Active (FAZ 27)  
**RTO target:** ≤ 4 hours (P1 region loss)  
**RPO target:** ≤ 15 minutes (Postgres PITR + object replication)

## Failure scenarios

| Scenario | Severity | Primary action |
|----------|----------|----------------|
| Single pod crash | P3 | K8s restart; read-only FS / non-root preserved |
| AZ outage | P2 | Fail over to standby AZ (same region) |
| Region loss | P1 | Promote DR region; DNS cutover |
| Ransomware / mass delete | P1 | Freeze writes; restore from immutable backups |
| Public LLM egress break-glass misuse | P1 | Revoke profile; rotate keys; audit |

## DR topology (target)

```text
Primary region                    DR region
┌ platform-backend ┐              ┌ platform-backend ┐
│ ai-orchestrator  │  async repl  │ warm standby     │
│ postgres (PITR)  │ ───────────▶ │ postgres replica │
│ object store     │ ───────────▶ │ replica bucket   │
└──────────────────┘              └──────────────────┘
```

## Failover steps (region)

1. **Detect** — multi-signal (health, synthetic probes, cloud status).
2. **Decide** — incident commander confirms RTO clock start.
3. **Freeze** — stop primary writes (scale API to 0 or maintenance mode).
4. **Promote** — promote Postgres replica; switch object endpoint to replica bucket.
5. **Cut DNS / ingress** — point traffic to DR ingress TLS certs.
6. **Validate** — run `scripts/verify-faz27` subset + tenant isolation smoke.
7. **Communicate** — status page; estimate RPO actual from last WAL.

## Failback

Only after primary health + data catch-up. Prefer reverse replication then controlled cutover during low traffic.

## Data integrity priorities

1. `operations.legal_holds` must survive — never skip when restoring older dumps.
2. `audit.entries` are append-only; restore may include archive markers (`archived_at`).
3. Transcript objects must match DB content hashes before enabling download URLs.

## Contacts / escalation

Maintain on-call rotation separately. This runbook does not store secrets or personal contacts.
