# FAZ-17 Evidence Validation / Quality Gate Binding

**Phase:** FAZ 17  
**Date:** 2026-07-25  
**Status:** Complete (gated AI→note handoff + auth-bound validation HTTP)

## 1. Faz özeti

Evidence validation domain (10 kural, `EvidenceValidationApi`, V183, quality gate) zaten olgundu; FAZ 16 handoff ise gate olmadan `mapAiCandidates` çağırıyordu. Bu turda handoff önce validation çalıştırıyor: yalnızca **PASSED** / **PASSED_WITH_WARNINGS** note üretir; **MANUAL_REVIEW_REQUIRED** / **REJECTED** note oluşturmaz (ManualReviewCase validation servisi tarafından açılır). Auth-bound HTTP yüzeyi eklendi.

## 2. Akış

```text
POST /ai-jobs/execute-next  (CHUNK_EXTRACTION başarı)
        ↓
MeetingNoteHandoffPort (platform)
        ↓
ValidationCandidateMapper (draft + segments + speakers→participants)
        ↓
EvidenceValidationApi.validate
        ↓
  PASSED / PASSED_WITH_WARNINGS → mapAiCandidates → meetingNoteId
  MANUAL_REVIEW / REJECTED     → Optional.empty() (note yok)
        ↓
GET /evidence-validation/...  (history, metrics, manual-review-cases, override)
```

Segment id projeksiyonu: `UUID.nameUUIDFromBytes("segment:" + id)` — evidence link ile tutarlı olmalı.

## 3. Bu turda değişenler

### Handoff gate
- `MeetingNoteHandoffPort.HandoffCommand` — `transcriptId`
- `ValidationCandidateMapper` — draft/segments/participants
- `MeetingIntelligenceHandoffAdapter` — validate → gate → conditional map; `QUALITY_GATE_*` audit
- `MeetingIntelligencePlatformConfiguration` — validation InMemory beans + gated handoff bean

### Auth HTTP
- `EvidenceValidationAuthController` — `/api/v1/evidence-validation/validate|.../override|history|metrics|manual-review-cases`

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Gate before mapAiCandidates | ✓ |
| PASSED → note + provenance | ✓ |
| MANUAL_REVIEW / REJECTED → no note | ✓ |
| ManualReviewCase on soft/hard fail | ✓ (service) |
| Speakers as synthetic participants | ✓ |
| Stable segment UUID projection | ✓ |
| Auth-bound validate/history/metrics/override/review | ✓ |
| Tenant-scoped history via jobId | ✓ |
| JDBC validation repos | deferred |
| Override → auto-retry map | deferred |
| Amount extraction into amountText | deferred (mapper set etmiyor) |

## 5. Testler

- `ValidationCandidateMapperTest` (1)
- `MeetingIntelligenceAuthBindingTest` (4): pass handoff + gate history; unknown owner blocks note + opens review; permission; foreign tenant
- `EvidenceValidationQualityGateTest` (module, regresyon)
- `FinalNoteDraftMapperTest`, `AiProcessingAuthBindingTest`

```bash
export JAVA_HOME=$PWD/.tools/jdk-21
./mvnw -pl modules/meeting-intelligence test -Dtest='EvidenceValidationQualityGateTest'
./mvnw -pl apps/platform-backend test -Dtest='MeetingIntelligenceAuthBindingTest,ValidationCandidateMapperTest,FinalNoteDraftMapperTest,AiProcessingAuthBindingTest'
```

## 6. Bilinen riskler

- AI pipeline zaten evidence zorunlu kılar; gate’te empty-evidence hard-reject çoğu zaman pipeline’da düşer — gate binding testi unknown-owner (speaker değil) ile yapıldı.
- Handoff hataları job SUCCEEDED sonrası oluşursa note olmayabilir; job yine succeeded kalır.
- InMemory only; production segment/participant kaynakları JDBC/transcript API’ye taşınmalı.
- Topics hâlâ map edilmiyor (FAZ 16 limit).
