# Wave 9 — SLO / DR / load

## Scope

Numerical SLIs with burn-rate alert sketches, backup restore drill checklist, and enterprise readiness gate matrix (Gates 1–11).

## Deliverables

| Path | Purpose |
|------|---------|
| `docs/reviews/ENTERPRISE-PRODUCTION-READINESS.md` | Gates 1–11 Done/Partial/Open matrix |
| `docs/operations/SLO-ALERTS.md` | SLI targets + Prometheus-style burn-rate sketches |
| `docs/operations/BACKUP-RESTORE-DRILL.md` | Quarterly restore drill checklist → [`BACKUP-RESTORE-RUNBOOK.md`](../operations/BACKUP-RESTORE-RUNBOOK.md) |

## SLI summary

| SLI | Target |
|-----|--------|
| Availability (`/api/health`) | 99.9% / 30d |
| Latency p95 (`/api/**`) | ≤ 800 ms |
| Outbox lag | ≤ 60 s |
| DLQ rate | ≤ 0.1% / 1h |

See full alert definitions in [`SLO-ALERTS.md`](../operations/SLO-ALERTS.md).

## DR / backup

- Operational procedures: [`BACKUP-RESTORE-RUNBOOK.md`](../operations/BACKUP-RESTORE-RUNBOOK.md)
- Region failover: [`DISASTER-RECOVERY-RUNBOOK.md`](../operations/DISASTER-RECOVERY-RUNBOOK.md)
- Drill checklist: [`BACKUP-RESTORE-DRILL.md`](../operations/BACKUP-RESTORE-DRILL.md)

## Load testing (recommended next)

| Scenario | Tooling | SLO under test |
|----------|---------|----------------|
| Portal read path | k6 / Gatling | Latency p95, availability |
| Outbox relay catch-up | Synthetic enqueue burst | Outbox lag |
| Graph webhook burst | Replay fixture notifications | DLQ rate |

Existing in-repo baseline: `DailyMeetingLoadScenarioTest` (in-memory; not durable load proof).

## Exit criteria

- [x] Enterprise readiness matrix with gate statuses
- [x] Numerical SLIs + burn-rate alert sketches
- [x] Backup restore drill checklist linked to runbook
- [ ] Load test report in `artifacts/` (deferred)
- [ ] Gate 11 production declaration (blocked on gates 6–7, 9–10)

## Gate 11 blockers

1. MSAL SPA + Entra E2E (Wave 6 deferred)
2. CD image push + digest promotion enabled (Wave 8)
3. Observability alerts deployed from `SLO-ALERTS.md`
4. Green `./scripts/test-all` on release candidate
