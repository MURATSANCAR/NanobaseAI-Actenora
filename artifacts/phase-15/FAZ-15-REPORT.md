# FAZ 15 Report — Multi-Model Routing ve Local Fallback

**Date:** 2026-07-25  
**Status:** Implemented (domain + application + in-memory adapters + unit tests)

## 1. Faz özeti

AI Processing bounded context artık en az iki local model rolünü (`FastExtractionModel`, `Qwen27FinalModel`) destekliyor. Gerçek ikinci model yoksa FastExtraction **mock deployment** ile seed edilir; routing ve orchestration yine de uçtan uca çalışır.

Fallback zinciri: `PRIMARY → SAME_MODEL_OTHER_DEPLOYMENT → ALTERNATE_LOCAL_MODEL → RETRY_QUEUE → MANUAL_REVIEW`.

Champion/challenger shadow altyapısı eklendi; **consensus varsayılan OFF**; shadow sonucu production route’u etkilemez.

## 2. Değişen bounded context'ler

| BC | Etki |
|----|------|
| `ai-processing` | Multi-model router, fallback, provenance, attempt history, shadow, quality metrics, API, Flyway V2 |
| `model-management` | Dokunulmadı (catalog port ile gevşek bağ; bootstrap in-memory seed) |

## 3. Eklenen/değiştirilen dosyalar

### Domain (`domain/routing/`)
- `ModelRole`, `InferenceTaskType`, `FallbackStep`, `ConsensusMode`, `ValidationModelPreference`
- `LocalDeploymentRef`, `TenantRoutingPolicy`, `RoutingRequest`, `CandidateEvaluation`
- `RoutingDecision`, `ModelChangeProvenance`, `AttemptRecord`, `AttemptHistory`
- `ShadowExecution`, `ModelQualityMetrics`, `ModelQualitySnapshot`
- `TaskRoleMapping`, `CriticalFallbackPolicy`, `MultiModelRouter`

### Application
- Ports: catalog, decision store, attempt history, shadow, quality metrics, retry queue
- `MultiModelRoutingService`, `AiProcessingFacade` → `MultiModelRoutingApi`

### Infrastructure
- In-memory adapters + `DefaultModelRoleBootstrap` (mock FastExtraction + Qwen27Final + Validation)

### API
- `MultiModelRoutingApi`, `MultiModelRoutingDtos`

### Tests
- `MultiModelRouterTest` (11 cases), `AiProcessingFacadeRoutingTest` (1 case)

## 4. Migration'lar

`aiprocessing/V174__multi_model_routing.sql`:
- `routing_decisions`
- `model_change_provenance`
- `attempt_history`
- `shadow_executions`
- `model_quality_metrics`
- `retry_queue`

## 5. API değişiklikleri

Yeni public façade: `MultiModelRoutingApi` (FAZ 12 `AiProcessingApi` ile birleştirilmedi; pipeline API gibi ayrı yüzey).

Task → role:
| Task | Role |
|------|------|
| `CHUNK_EXTRACTION` | `FAST_EXTRACTION` (mock OK) |
| `CANDIDATE_MERGE` | `QWEN27_FINAL` |
| `FINAL_NOTE` | `QWEN27_FINAL` |
| `VALIDATION` | policy: Qwen veya `VALIDATION` |

## 6. Event değişiklikleri

Yeni integration event yok (FAZ 15 kararları BC içi audit tablolarında). İleride outbox ile `RoutingDecisionRecorded` eklenebilir.

## 7. Model/prompt/schema değişiklikleri

- Model keys: `local.fast-extraction`, `local.qwen27-final`, `local.validation`
- Prompt/schema değişmedi

## 8. Güvenlik kontrolleri

- Cloud model yok; yalnız local deployment
- Alternate model tenant allowlist ile
- Critical job’larda quality downgrade yasaklanabilir
- Shadow/challenger production sonucunu değiştiremez; consensus OFF

## 9. Çalıştırılan komutlar

```bash
export JAVA_HOME=.../.tools/jdk-21.0.11+10/Contents/Home
./mvnw -pl modules/shared-kernel install -Dmaven.test.skip=true
./mvnw -pl modules/ai-processing compile
./mvnw -pl modules/ai-processing surefire:test \
  -Dtest='MultiModelRouterTest,AiProcessingFacadeRoutingTest'
```

## 10. Test sonuçları

```
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Kapsanan senaryolar:
- primary unavailable → same model second deployment
- alternate allowed (quality downgrade flag)
- alternate forbidden
- critical no downgrade
- shadow execution (production unaffected)
- all unavailable → retry → manual review
- provenance correctness
- attempt history + model quality metrics
- mock FastExtraction bootstrap
- VALIDATION role preference

## 11. Bilinen riskler

- Paralel FAZ’lar `ai-processing` içinde yoğun değişiklik yapıyor; `AiProcessingApi` FAZ 12’ye ait — routing ayrı API’de tutuldu.
- Flyway `V174` numarası diğer fazlarla birleştirilirken gözden geçirilmeli.
- Persistence henüz in-memory; JDBC adapter FAZ sonrası.
- Reactor’da transcript/shared-kernel test compile kırıkları FAZ 15 dışı.

## 12. Service extraction etkisi

Routing kararları `aiprocessing` şemasında; Model Gateway service extraction’ta bu tablolar `modelgw`/ai-processing ile birlikte taşınır. Domain router Spring bilmez — extraction kolay.

## 13. Sonraki faza geçiş durumu

Hazır: FAZ 16 Meeting Intelligence, production worker bağlama (FAZ 13 provider + FAZ 12 job claim → FAZ 15 route).  
Bekleyen: catalog’u FAZ 11 `ModelManagementApi` ile bağlamak; JPA persistence; outbox audit event.
