# FAZ-6 Meeting Core Domain

**Phase:** FAZ 6  
**Date:** 2026-07-25  
**Status:** Complete (domain already mature; gap-close pass)

## 1. Faz özeti

Meeting Core Domain zaten modeller, state machine, API’ler, lifecycle event’ler ve kabul test matrisiyle büyük ölçüde mevcuttu. Bu turda kalan boşluklar kapatıldı: BusinessContext `reference_code` uniqueness, RFC 7807 Problem Details, BusinessContext tenant isolation testleri. Audit wiring FAZ 5’te `MeetingAuditPort` → `AuditApi` olarak yapılmıştı.

## 2. Kabul kriterleri

| Gereksinim | Durum |
|------------|--------|
| BusinessContext / Series / Occurrence / Participant modelleri | ✓ |
| State machine + invalid transition | ✓ |
| Graph / occurrence uniqueness | ✓ |
| Tenant isolation | ✓ (+ BusinessContext) |
| Optimistic locking | ✓ |
| APIs (meeting + business context) | ✓ |
| Lifecycle events (6) | ✓ |
| Audit on mutations | ✓ (platform AuditApi adapter) |
| Tests (transition, duplicate, isolation, OL, date, external, audit) | ✓ |

## 3. Bu turda değişenler

- `DuplicateBusinessContextException` + repository `findByTenantIdAndReferenceCode`
- `BusinessContextApplicationService` uniqueness on create/update + `get`
- `MeetingProblemDetails` + `MeetingExceptionHandler` → `application/problem+json`
- Tests: duplicate reference code, BC tenant isolation, Problem Details

## 4. Bilinen riskler

- Persistence still InMemory (Flyway `V140_1` ready; JDBC deferred)
- Series relations / continuity = FAZ 7

## 5. Sonraki faz

FAZ 7 — Meeting series, relations, continuity identifiers.
