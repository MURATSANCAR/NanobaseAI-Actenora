# FAZ-14 Extraction Pipeline ↔ Job Path

**Phase:** FAZ 14  
**Date:** 2026-07-25  
**Status:** Complete (InMemory segment source + pipeline wired into claim/execute)

## 1. Faz özeti

Extraction pipeline (normalize→chunk→extract→repair/validate→merge→final note) zaten olgundu. Bu turda FAZ 13 job executor'a bağlandı: `CHUNK_EXTRACTION` job'ları transcript segmentlerini okuyup pipeline üzerinden koşuyor; diğer task tipleri tek-shot provider yolunda kaldı.

## 2. Akış

```text
POST /ai-jobs  (TRANSCRIPT_EXTRACTION / CHUNK_EXTRACTION)
        ↓
POST /ai-jobs/execute-next
        ↓
AiJobInferenceExecutor
  → TranscriptSegmentSourcePort.segmentsFor(tenant, transcriptId)
  → ExtractionPipelineService.run(...)
  → completeAttempt / failAttempt
```

Non-extraction jobs: FAZ 13 direct `LocalModelProvider.submitInference`.

## 3. Bu turda değişenler

- `TranscriptSegmentSourcePort` + `TranscriptSegmentSourceAdapter` (transcript repo → `SegmentInput`)
- `InMemoryTranscriptSegmentSource` (module tests)
- `AiJobInferenceExecutor` — extraction branch + pipeline failure mapping
- `PipelineFailureMapper` — `FailureCategory` → `ProviderFailureCategory`
- `ExtractionPipelineService` — `MODEL_UNAVAILABLE` artık job-level retryable (`permanentFailure=false`)
- Platform beans: `ModelRuntimePort` (Qwen bridge), `ExtractionPipelineService`, `ExtractionPipelineApi`, segment source
- Default served models: `qwen2.5-32b-instruct,qwen-local`

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Chunk binding from transcript | ✓ segment source |
| Schema validate + JSON repair | ✓ pipeline (existing) |
| Merge + deterministic validate | ✓ |
| Job claim → pipeline → SUCCEEDED | ✓ |
| Empty segments → DEAD | ✓ EVIDENCE_MISSING |
| Model unavailable → QUEUED | ✓ retryable |
| Unrepairable JSON → DEAD | ✓ |
| Auth-bound execute-next | ✓ |
| InMemory-first | ✓ |
| Persist FinalNoteDraft | deferred |
| Per-deployment runtime | deferred |
| Prompt Pack (FAZ 16) | deferred |

## 5. Testler

- `AiJobInferenceExecutorPipelineTest` (4)
- `PipelineFailureMapperTest` (1)
- `TranscriptSegmentSourceAdapterTest` (2)
- `AiProcessingAuthBindingTest` (+ extraction execute-next)
- `ExtractionPipelineServiceTest` (MODEL_UNAVAILABLE assertion updated)
- Regresyon: FAZ 13 executor + provider tests green

## 6. Bilinen riskler

- Final note çıktısı attempt metrics'e yazılıyor; domain store yok
- Prompt id: job `promptVersion` `meeting.*` değilse default extraction prompt kullanılıyor
- Tek shared `ModelRuntimePort` (Qwen descriptor); deployment başına runtime yok
- Normalized transcript run vs raw segments: pipeline kendi `SegmentNormalizer`'ını kullanıyor

## 7. Sonraki faz

FAZ 15 — Multi-model routing provenance/shadow'un job claim path'ine operasyonel bağlanması (veya Prompt Pack / Meeting Intelligence — backlog sırasına göre).
