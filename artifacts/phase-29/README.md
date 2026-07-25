# FAZ 29 evidence index

- Date (UTC): 2026-07-25T18:04:07Z
- Commit: 5be2fd2
- Verdict: **NOT PRODUCTION-READY** (see docs/reviews/PRODUCTION-READINESS-REPORT.md)

## Artifacts in this folder

| File | Purpose |
|------|---------|
| `FAZ-29-EVIDENCE.txt` | Snapshot of suite/audit/docker/SBOM/TODO probes |
| `test-all.log` / `test-all-rerun.log` / `test-all-final.log` | Full suite attempts |
| `observability-clean-test.log` | Clean observability package (10 tests pass) |
| `nonjava-tests.log` | AI + pnpm package/app tests |
| `web-portal-test.log` | Frontend failure detail |
| `docker-check.log` | Docker daemon missing |
| `sbom.log` | SBOM generation |
| `dependency-scan.log` | Tool availability + pnpm audit |
| `stop-condition-scan.txt` | TODO/mock/Flyway/InMemory scan |
| `extra-checks.log` | Migration collisions + security probes |

## Mandatory docs produced

### Reviews
- `docs/reviews/PRODUCTION-READINESS-REPORT.md`
- `docs/reviews/MICROSERVICE-EXTRACTION-READINESS.md`
- `docs/reviews/MULTI-MODEL-READINESS.md`
- `docs/reviews/DISTRIBUTED-FAILURE-TEST-REPORT.md`

### Operations
- `docs/operations/MODEL-POOL-RUNBOOK.md`
- `docs/operations/SERVICE-EXTRACTION-RUNBOOK.md`
- `docs/operations/INCIDENT-RUNBOOK.md`
- `docs/operations/ROLLBACK-RUNBOOK.md`
