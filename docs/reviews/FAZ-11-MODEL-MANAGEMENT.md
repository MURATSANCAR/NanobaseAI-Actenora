# FAZ-11 Model Management Control Plane

**Phase:** FAZ 11  
**Date:** 2026-07-25  
**Status:** Complete (InMemory registry + auth/audit/catalog platform wiring)

## 1. Faz özeti

Model registry domain (definition/deployment/capability/health) ve `ModelRegistryService` zaten olgundu. Bu turda: FAZ 4 auth binding (`TenantSecurityContext` + `MODEL_CONTROL`), AuditApi adapter, cloud provider reject, routing catalog projection (`LocalDeploymentCatalogPort`), GET model endpoint.

## 2. API

```text
POST /api/v1/model-control/models
GET  /api/v1/model-control/models/{modelKey}
PUT  /api/v1/model-control/models/{modelKey}
PUT  /api/v1/model-control/models/{modelKey}/capabilities/{capability}
POST /api/v1/model-control/models/{modelKey}/enable|disable|drain
POST /api/v1/model-control/deployments
POST /api/v1/model-control/deployments/{key}/heartbeat
GET  /api/v1/model-control/health
```

Actor: `TenantSecurityContext` + `MODEL_CONTROL` permission (X-Actor-* kaldırıldı).

## 3. Bu turda değişenler

- `ModelControlPlaneController` — auth-bound; Problem Details + `CLOUD_PROVIDER_REJECTED`
- `LocalProviderGuard` — openai/anthropic/… ve cloud host reject
- `ModelManagementPlatformConfiguration` — AuditApi Primary + PreferRegistry catalog
- Tests: `ModelAuthBindingTest` (permission, audit, allowlist, catalog)

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Register / update / enable / disable / drain | ✓ |
| Deployments + heartbeat | ✓ |
| Health view | ✓ |
| Tenant compatibility (Policy allowlist) | ✓ (FAZ 5 + test) |
| Auth-bound HTTP | ✓ |
| Audit on mutations | ✓ AuditApi |
| Local-only provider guard | ✓ |
| Catalog → AI routing port | ✓ PreferRegistry + seed fallback |
| InMemory repos | ✓ |
| JDBC | deferred (Flyway V162 ready) |

## 5. Testler

- `ModelRegistryServiceTest` (9)
- `ModelAuthBindingTest` (5)
- `ModelManagementModuleTest` (1)

## 6. Bilinen riskler

- Capability→`ModelRole` mapping heuristic (VALIDATION / FINAL_NOTE / TRANSCRIPT_EXTRACTION)
- Heartbeat/agent auth same `MODEL_CONTROL` (narrower agent role later)
- Outbox integration events still stubs

## 7. Sonraki faz

FAZ 12 — Multi-model routing / AI job orchestration (catalog artık bağlanabilir).
