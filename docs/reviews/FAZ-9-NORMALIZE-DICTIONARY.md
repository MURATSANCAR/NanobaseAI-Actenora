# FAZ-9 VTT Normalize & Tenant Dictionary

**Phase:** FAZ 9  
**Date:** 2026-07-25  
**Status:** Complete (domain mature; API + wiring + versioned runs)

## 1. Faz özeti

Normalizer, dictionary matcher, speaker resolver, NormalizationRun ve unit testler zaten vardı. Bu turda: dictionary HTTP CRUD, normalize/renormalize gerçek pipeline’a bağlandı, idempotency’ye dictionary id eklendi, immutable per-run object key, duplicate Flyway V151 çakışması giderildi.

## 2. API

```text
POST /api/v1/transcript-dictionaries
GET  /api/v1/transcript-dictionaries
GET  /api/v1/transcript-dictionaries/{id}
POST /api/v1/transcript-dictionaries/{id}/entries

POST /api/v1/transcripts/{id}/normalize?dictionaryId=
POST /api/v1/transcripts/{id}/renormalize?dictionaryId=   # aynı: segment’lerden normalize
POST /api/v1/transcripts/{id}/reparse                     # raw VTT → segments (ingest)
```

Tenant: `TenantSecurityContext` / header fallback (FAZ 8 ile aynı).

## 3. Bu turda değişenler

- `TenantDictionaryApplicationService` + `TenantDictionaryController`
- `TranscriptController` / `TranscriptApi` → `TranscriptNormalizationService`
- `NormalizationVersion.compose(dictionaryId, revision)`
- `TenantObjectKeys.normalizedRun(...)` — her run ayrı immutable key
- Flyway: `V151` event backbone → `V153` (duplicate version fix)
- Tests: dictionary CRUD isolation, revision → new run

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Tenant dictionary (PRODUCT/COMPANY/PROJECT/SPEAKER) | ✓ |
| Alias + longest-first term rewrite | ✓ (domain) |
| Speaker exact/alias/ambiguous | ✓ (domain) |
| Versioned / idempotent normalize runs | ✓ (+ dict id) |
| Renormalize executes normalize (not status-only) | ✓ HTTP |
| Dictionary CRUD HTTP | ✓ |
| Per-run immutable object keys | ✓ |
| Tenant isolation | ✓ |
| Reparse ≠ renormalize | ✓ |
| Flyway unique versions | ✓ V151/V152/V153 |
| Domain tests | ✓ 30 focused PASS |

## 5. Bilinen riskler

- Persistence still InMemory (Flyway `V151` tables ready; JDBC deferred)
- Normalize domain events returned but not yet forced through outbox audit
- Concurrent idempotency not atomic (check-then-act)
- Parse issues still not persisted into normalize input

## 6. Sonraki faz

FAZ 10 — Event backbone / outbox (V153 migration hazır).
