# FAZ-7 Meeting Series, Relations & Continuity

**Phase:** FAZ 7  
**Date:** 2026-07-25  
**Status:** Complete (domain already mature; wiring + HTTP + Problem Details)

## 1. Faz özeti

Relation/continuity domain, SeriesResolver, suggestion approve/reject, duplicate/cyclic invariants ve test matrisi zaten vardı. Bu turda Spring wiring, HTTP surface, occurrence continuity adapter (FAZ 6 repos), relation audit → MeetingAuditPort ve RFC 7807 exception mapping eklendi.

## 2. API

```text
POST /api/v1/meeting-relations
GET  /api/v1/meeting-occurrences/{id}/relations
POST /api/v1/meeting-relation-suggestions/{id}/approve
POST /api/v1/meeting-relation-suggestions/{id}/reject
GET  /api/v1/meeting-occurrences/{id}/continuity
```

Tenant id authenticated context / `TenantContextPort` üzerinden; body’den alınmaz. Join URL identity değildir.

## 3. Wiring

- `RepositoryOccurrenceContinuityPort` — occurrence/series → continuity snapshots
- `MeetingAuditRelationAuditPort` — relation mutations → meeting audit (platform AuditApi)
- Beans in `MeetingModuleConfiguration`

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Continuity key (series/iCal/originalStart) | ✓ |
| Immutable Graph event id | ✓ |
| Join URL not identity | ✓ (SeriesResolver + tests) |
| Manual relation API | ✓ HTTP |
| AI suggestion separate / no auto-create | ✓ |
| Confidence + reason | ✓ |
| Duplicate / cyclic checks | ✓ |
| Audit relation changes | ✓ |
| previous/next projection | ✓ continuity endpoint |
| Domain tests | ✓ existing |

## 5. Bilinen riskler

- InMemory relation repos (Flyway V141 ready)
- Brief product path in meeting-intelligence still separate (continuity projection exposed for handoff)

## 6. Sonraki faz

FAZ 8 — Transcript ingestion & object storage.
