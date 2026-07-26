# FAZ-15 Routing Provenance ↔ Job Claim Path

**Phase:** FAZ 15 (operational binding)  
**Date:** 2026-07-25  
**Status:** Complete (role-based routing, provenance, attempt history ve quality metrics job yolunda)

## 1. Faz özeti

FAZ 15 domain/application katmanı (MultiModelRouter, fallback zinciri, provenance, shadow, quality metrics) hazırdı ama sadece `POST /ai-routing/route` ile manuel çağrılabiliyordu; gerçek job execution FAZ 12 capability route'unu kullanıyor, hiçbir audit kaydı üretmiyordu. Bu turda claim → execute yolu FAZ 15 router'ına bağlandı.

## 2. Akış

```text
POST /ai-jobs/execute-next
        ↓
AiJobService.claimNext
        ↓
JobRoutingCoordinatorPort.routeForExecution(job, taskType)   ← FAZ 15 router
   ├─ production route yok  → failAttempt(HEALTH_DEGRADED, retry queue'ya göre retryable)
   └─ production route var  → deployment/model/servedModel override
        ↓
extraction pipeline (CHUNK_EXTRACTION) | direct provider (diğerleri)
        ↓
recordSuccess / recordFailure → attempt history + model quality metrics
```

Routing kararı ve provenance her claim'de `routing_decisions` / `model_change_provenance` deposuna yazılır (halen InMemory).

## 3. Bu turda değişenler

- `JobRoutingCoordinatorPort` (+ `RoutedExecution`) — execution path ile FAZ 15 arasındaki sözleşme
- `MultiModelRoutingJobCoordinator` — job → `RoutingRequest`/`TenantRoutingPolicy` çevirisi, attempt outcome geri yazımı
- `AiJobInferenceExecutor` — claim'de routing, production route yoksa inference yapmadan attempt kapatma, routed deployment/model/servedModel override
- `AiRoutingProperties` (`actenora.ai.routing.enabled`, `shadow-execution-enabled`) + `application.yml`
- Platform beans: `jobRoutingCoordinator`, executor'a coordinator injection (`enabled=false` ise FAZ 12 route yetkili kalır)
- Yeni auth-bound okuma uçları: `GET /ai-routing/jobs/{jobId}/decisions|provenance|shadow`, `GET /ai-routing/model-quality`

Tenant policy eşlemesi: `allowedModelKeys` → alternate allowlist, `job.fallbackPermitted` → quality downgrade izni, `isCriticalFallbackAllowed=false` → critical job'da downgrade yasağı. Consensus `OFF` sabit; shadow varsayılan kapalı.

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Claim → role-based route | ✓ coordinator |
| Provenance kaydı (model değişimi) | ✓ fallback'te |
| Attempt history STARTED→SUCCEEDED/FAILED | ✓ |
| Model quality metrics (latency, schema pass) | ✓ |
| Unhealthy primary → same-model secondary | ✓ test |
| Route yok → retry queue + job QUEUED | ✓ HEALTH_DEGRADED |
| Provider çağrısı yapılmadan fail | ✓ |
| Shadow production'ı etkilemez | ✓ consensus OFF, default kapalı |
| Tenant-scoped audit okuma | ✓ job tenant kontrolü |
| Kill switch | ✓ `actenora.ai.routing.enabled=false` |
| JDBC persistence | deferred |
| Shadow challenger'ın gerçekten koşturulması | deferred |
| Outbox `RoutingDecisionRecorded` | deferred |

## 5. Testler

- `AiJobRoutingProvenanceTest` (4): success→metrics, provider failure→FAILED attempt, route yok→requeue, unhealthy primary→secondary + provenance
- `AiProcessingAuthBindingTest` (+2): execute-next sonrası decisions/quality/shadow uçları, foreign tenant reddi
- Regresyon: `modules/ai-processing` 84 test, `apps/platform-backend` seçili 22 test yeşil

```bash
export JAVA_HOME=$PWD/.tools/jdk-21
./mvnw -pl modules/ai-processing test
./mvnw -pl apps/platform-backend test -Dtest='AiProcessingAuthBindingTest,LocalProviderFactoryTest,TranscriptSegmentSourceAdapterTest,ModelAuthBindingTest'
```

## 6. Bilinen riskler

- İki router aynı anda yaşıyor: FAZ 12 capability route admission'da, FAZ 15 role route execution'da. Deployment seçimi execution'da FAZ 15'e ait; job aggregate'indeki `selectedRoute` güncellenmiyor (audit ayrı tabloda).
- Extraction pipeline tek shared `ModelRuntimePort` kullandığı için routed deployment extraction yolunda etkisiz; kayıt yine de tutuluyor.
- Retry queue'ya düşen job'lar hem AI job kuyruğunda hem FAZ 15 retry queue'da; manual review escalation halen manuel çağrı.
- Persistence InMemory; `V174` migration henüz bağlı değil.

## 7. Sonraki faz

FAZ 16 — Meeting Intelligence / Prompt Pack (veya JDBC persistence + outbox audit event, backlog sırasına göre).
