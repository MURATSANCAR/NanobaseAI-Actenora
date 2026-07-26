# FAZ-12 Multi-Model Routing / AI Job Orchestration

**Phase:** FAZ 12  
**Date:** 2026-07-25  
**Status:** Complete (InMemory job stack + registry catalog bridge + auth-bound HTTP)

## 1. Faz özeti

Domain (CapabilityModelRouter, FairJobScheduler, AiProcessingApi, MultiModelRouter) zaten olgundu. Bu turda platform boşlukları kapatıldı: registry → `ModelCatalogPort`, Spring wiring, TenantAiPolicy bağlama, auth-bound HTTP, PreferRegistry unhealthy projection (FAZ 15 failover/provenance).

## 2. API

```text
POST /api/v1/ai-jobs
GET  /api/v1/ai-jobs/{jobId}
POST /api/v1/ai-jobs/{jobId}/cancel
POST /api/v1/ai-jobs/claim-next
POST /api/v1/ai-jobs/{jobId}/admin-override
POST /api/v1/ai-routing/route
```

Tenant id yalnızca `TenantSecurityContext`. Allowlist `/ai-routing/route` için `TenantAiPolicyPort.allowedModelKeys`.

## 3. Bu turda değişenler

- `AiProcessingPlatformConfiguration` — jobs/attempts InMemory, `PreferRegistryModelCatalog`, CapabilityModelRouter, FairJobScheduler, AiProcessingApi, MultiModelRoutingApi
- `PreferRegistryModelCatalog` — FAZ 11 registry → `RoutableCandidate` (+ seed bootstrap)
- PreferRegistry LocalDeployment — unhealthy deployment’lar `healthy=false` olarak kalır (omit edilmez)
- `AiProcessingController` — auth + Problem Details
- `DefaultModelCatalogBootstrap` — registry boşken seed
- Tests: `AiProcessingAuthBindingTest` (4) + ModelAuthBinding unhealthy failover

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Capability-based route (vendor string değil) | ✓ CapabilityModelRouter |
| Tenant allowlist | ✓ TenantAiPolicyPort |
| Healthy-only selection | ✓ |
| Fallback + critical forbid | ✓ (domain + scheduler) |
| Multi-model provenance path | ✓ MultiModelRoutingApi + PreferRegistry fix |
| Auth-bound HTTP | ✓ |
| Registry catalog bind | ✓ PreferRegistryModelCatalog |
| InMemory-first | ✓ |
| JDBC (V173/V174) | deferred |
| Dual-stack merge (FAZ 12 ↔ 15) | deferred (ayrı yüzeyler korundu) |

## 5. Testler

- `AiJobAdmissionRoutingSchedulerTest` (module)
- `MultiModelRouterTest` / `AiProcessingFacadeRoutingTest`
- `AiProcessingAuthBindingTest` (4) — admit, allowlist reject, unhealthy failover provenance, permission deny
- `ModelAuthBindingTest` (+ unhealthy visibility)

## 6. Bilinen riskler

- İki routing yüzeyi: job admit (`CapabilityModelRouter`) vs task-role (`MultiModelRouter`)
- Runtime concurrency/queueDepth registry projection’da henüz 0
- `QWEN27_FINAL` role adı (M3 rename borç)
- JDBC adapters yok

## 7. Sonraki faz

FAZ 13 — Local LLM provider adapters / inference runtime bağlama (job claim → provider execute).
