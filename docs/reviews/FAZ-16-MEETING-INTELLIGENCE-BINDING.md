# FAZ-16 Meeting Intelligence Binding + Thin Prompt Pack

**Phase:** FAZ 16  
**Date:** 2026-07-25  
**Status:** Complete (InMemory MI wiring + AI→note handoff + immutable prompt versions)

## 1. Faz özeti

Meeting Intelligence domain/API/Flyway zaten olgundu ama platform'da bean yoktu; extraction başarılı olsa bile `FinalNoteDraft` atılıyordu. Bu turda MI InMemory olarak composition root'a bağlandı, auth-bound HTTP yüzeyi eklendi ve başarılı `CHUNK_EXTRACTION` job'ları `mapAiCandidates` ile kurumsal note objelerine dönüştürülüyor. İnce Prompt Pack: publish artık önceki versiyonları silmeden yeni immutable sürüm üretir.

## 2. Akış

```text
POST /ai-jobs  (CHUNK_EXTRACTION)
        ↓
POST /ai-jobs/execute-next
        ↓
AiJobInferenceExecutor → ExtractionPipelineService → FinalNoteDraft
        ↓
MeetingNoteHandoffPort (platform adapter)
        ↓
MeetingIntelligenceApi.mapAiCandidates → MeetingNote + decisions/actions/evidence
        ↓
GET /meeting-notes/{noteId}  (MEETING_READ, tenant-scoped)
```

Modulith: AI Processing `meetingintelligence`'a bağımlı değil; handoff port AI tarafında, adapter platform'da.

## 3. Bu turda değişenler

### Meeting Intelligence binding
- `MeetingIntelligencePlatformConfiguration` — InMemory repos, facade, AuditApi bridge, tenant context
- `MeetingIntelligenceAuthController` — auth-bound note/decision/action/risk/commitment/evidence uçları
- Module `MeetingIntelligenceController` — Spring MVC'den çıkarıldı (programatik yardımcı)
- `SecurityContextMeetingIntelligenceTenantPort`, `SystemClockPort`
- `MeetingNoteHandoffPort` + `MeetingIntelligenceHandoffAdapter` + `FinalNoteDraftMapper`

### AI job path
- `AiJobInferenceExecutor` — başarılı extraction sonrası handoff; `ExecutionOutcome.meetingNoteId`
- `ExecutionView.meetingNoteId` HTTP yanıtında

### Thin Prompt Pack
- `PromptRegistryPort` — `requireByVersionId`, `publish(...)`, `listVersions`
- `InMemoryPromptRegistry` — versiyon geçmişi; overwrite yok

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| `MeetingIntelligenceApi` Spring bean | ✓ |
| Auth-bound note GET/PUT | ✓ MEETING_READ/WRITE |
| Tenant isolation | ✓ foreign tenant → not found |
| AuditApi on AI→note map | ✓ `MEETING_NOTE_MAPPED_FROM_AI` |
| Extraction → mapAiCandidates | ✓ |
| Provenance (model/prompt/schema) on note version | ✓ |
| Decisions + evidence links persisted | ✓ |
| AI module ↛ MI dependency | ✓ handoff port |
| Failed pipeline creates no note | ✓ (handoff only on success) |
| Immutable prompt publish | ✓ |
| Resolve prior promptVersionId | ✓ |
| JDBC / prompt schema Flyway | deferred |
| Topics persistence | deferred (dropped in mapper) |
| Validation/ledger HTTP | deferred |
| Approval platform wiring | deferred |

## 5. Testler

- `InMemoryPromptRegistryTest` (2)
- `FinalNoteDraftMapperTest` (1)
- `MeetingIntelligenceAuthBindingTest` (3): handoff + readable note, permission deny, foreign tenant
- Regresyon: `AiProcessingAuthBindingTest`, executor/pipeline/routing tests yeşil
- `modules/ai-processing` + `modules/meeting-intelligence` suite

```bash
export JAVA_HOME=$PWD/.tools/jdk-21
./mvnw -pl modules/ai-processing,modules/meeting-intelligence test
./mvnw -pl apps/platform-backend test -Dtest='MeetingIntelligenceAuthBindingTest,FinalNoteDraftMapperTest,AiProcessingAuthBindingTest'
```

## 6. Bilinen riskler

- Topics AI draft'ta var; MI `AiCandidateBundle` topics alanını taşımıyor → drop
- Handoff failure currently propagates as uncaught exception after job already SUCCEEDED — production hardening'de try/catch + compensation gerekir
- Persistence InMemory; Flyway V181 tabloları henüz runtime'da kullanılmıyor
- Module controller Spring dışına alındı; eski client'lar platform auth controller'ı kullanmalı

## 7. Sonraki faz

FAZ 17 — Evidence validation / quality gate platform binding, veya Approval workflow wiring, veya JDBC persistence (backlog sırasına göre).
