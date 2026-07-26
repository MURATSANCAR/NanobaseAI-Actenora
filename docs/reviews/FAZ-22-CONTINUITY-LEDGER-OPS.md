# FAZ-22 Continuity Ledger Ops Binding

**Phase:** FAZ 22  
**Date:** 2026-07-25  
**Status:** Complete (brief / overdue / continuity / contradiction decide HTTP)

## 1. Faz özeti

FAZ 21 suggestion/event/contradiction list+propose’u bağladı; brief, overdue, continuity projection ve contradiction confirm/reject HTTP’de yoktu. Bu turda mevcut `ContinuityLedgerApi` yüzeyleri auth-bound olarak tamamlandı — yeni domain yok.

## 2. Akış

```text
GET  /continuity-ledger/overdue-commitments
GET  /continuity-ledger/occurrences/{id}/brief
GET  /continuity-ledger/occurrences/{id}/continuity
POST /continuity-ledger/contradictions/{id}/confirm|reject
```

Brief, previous occurrence continuity + overdue commitments + carry-overs’ı birleştirir.

## 3. Bu turda değişenler

- `ContinuityLedgerAuthController` — overdue, brief, continuity, contradiction decide
- `ContinuityLedgerAuthBindingTest` — confirm/reject, overdue+brief+continuity
- OpenAPI schemas for brief/commitment/continuity/contradiction decide

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Overdue commitments GET | ✓ |
| Meeting brief GET | ✓ |
| Continuity projection GET | ✓ |
| Contradiction confirm/reject | ✓ |
| MEETING_READ / WRITE | ✓ |
| Tenant-scoped reads | ✓ |
| Rebuild projections HTTP | deferred |
| Record commitment HTTP | deferred (seed via API in tests) |
| JDBC | deferred |
| confirmDelivered HTTP | deferred (delivery) |

## 5. Testler

- `ContinuityLedgerAuthBindingTest` (7): prior FAZ 21 + confirm, reject, overdue/brief/continuity
- `ContinuityLedgerServiceTest` regresyon

```bash
export JAVA_HOME=$PWD/.tools/jdk-21
./mvnw -pl apps/platform-backend test -Dtest='ContinuityLedgerAuthBindingTest'
```

## 6. Bilinen riskler

- Brief boş occurrence’da da 200 döner (empty carry-overs) — ürün UX’i bilmeli.
- Commitment record HTTP yok; operatör seed/domain çağrısı gerekir.
