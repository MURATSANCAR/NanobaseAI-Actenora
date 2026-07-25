# Reserved service extraction slots

These directories are **intentionally empty** in Phase 1.

Actenora starts as a modular monolith (`apps/platform-backend` + `modules/*`).
Workers and integration services will be cut out only when extraction criteria in
`docs/architecture/SERVICE-EXTRACTION-PLAYBOOK.md` (and ADR-001) are met.

| Reserved path | Future responsibility |
|---|---|
| `teams-integration-service/` | Microsoft Teams / Graph adapters |
| `transcript-worker/` | Transcript ingestion & normalization |
| `model-worker/` | Local model inference jobs |
| `document-renderer/` | Template → document rendering |
| `delivery-worker/` | Post-approval external delivery |

Do **not** add half-built placeholder services, unused dependencies, or empty Dockerfiles here.
Partial extraction drafts (if any) belong under `artifacts/wip-extraction/` until an extraction phase wires them.
