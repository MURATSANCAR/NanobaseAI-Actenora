# FAZ-8 Transcript Ingestion & Object Storage

**Phase:** FAZ 8  
**Date:** 2026-07-25  
**Status:** Complete (domain already mature; gap-close + platform wiring)

## 1. Faz özeti

VTT upload, SHA-256 dedup, immutable raw object keys, download authorization, reparse/renormalize, InMemory/S3 adapters ve unit test matrisi zaten vardı. Bu turda kalan boşluklar kapatıldı: bilinen meeting occurrence guard, meeting-backed platform store, metadata-only GET, tenant auth binding, `actenora.object-storage.enabled` hizalaması.

## 2. API

```text
POST /api/v1/transcripts/upload
GET  /api/v1/transcripts/{id}          # metadata only — no segments/raw
POST /api/v1/transcripts/{id}/download-authorization
POST /api/v1/transcripts/{id}/reparse
POST /api/v1/transcripts/{id}/renormalize
```

Tenant: `TenantSecurityContext` öncelikli; `X-Actenora-Tenant-Id` yalnızca fallback / mismatch kontrolü.

## 3. Bu turda değişenler

- `KnownMeetingOccurrenceStore` + upload → `UNKNOWN_MEETING_OCCURRENCE` (404)
- `TranscriptPlatformConfiguration` — occurrence repo + in-memory remember (event handler)
- `GET /{id}` + `TranscriptDetailResponse` (content yok)
- Controller tenant resolution auth-bound
- S3 bean: `actenora.object-storage.enabled` + `ConditionalOnMissingBean`
- Base `application.yml`: object-storage default `enabled=false`; local/prod `true`
- Tests: unknown meeting, get metadata

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Manual VTT upload | ✓ |
| Magic / MIME / size validation | ✓ |
| SHA-256 content hash + dedup | ✓ |
| Immutable raw object + tenant key prefix | ✓ |
| Known meeting occurrence guard | ✓ |
| Object storage port (InMemory / S3-MinIO) | ✓ |
| Authorized download + TTL cap | ✓ |
| Tenant isolation (object key + meta) | ✓ |
| Reparse / renormalize | ✓ |
| Retention delete | ✓ |
| Metadata GET without content | ✓ |
| RFC 7807 Problem Details | ✓ |
| Domain tests | ✓ (32 focused tests PASS) |

## 5. Bilinen riskler

- Transcript persistence still InMemory (JDBC deferred)
- Production MinIO requires `ACTENORA_OBJECT_STORAGE_ENABLED=true` + secrets
- Normalize / dictionary depth = FAZ 9

## 6. Sonraki faz

FAZ 9 — VTT normalize / tenant dictionary.
