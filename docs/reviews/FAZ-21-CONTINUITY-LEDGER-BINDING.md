# FAZ-21 Continuity Ledger Binding

**Phase:** FAZ 21  
**Date:** 2026-07-25  
**Status:** Complete (InMemory Continuity Ledger + auth HTTP)

## 1. Faz özeti

Continuity Ledger domain (event store, projections, suggestions, contradictions) zaten olgundu; platform’da bean/HTTP yoktu (FAZ 16’da deferred). Bu turda InMemory ledger composition root’a bağlandı ve auth-bound list/accept/reject yüzeyi eklendi. Note-Decision repository ile ledger DecisionHistory senkron değil — bilinçli ayrım.

## 2. Akış

```text
POST /continuity-ledger/suggestions          (MEETING_WRITE)
GET  /continuity-ledger/suggestions|events|contradictions  (MEETING_READ)
POST /continuity-ledger/suggestions/{id}/accept|reject
POST /continuity-ledger/contradictions
```

Accept FOLLOW_UP → continuity projection follow-up chain materialize olur. Reject → link oluşmaz.

## 3. Bu turda değişenler

### Module
- `ContinuityLedgerService.listEvents` / `listSuggestions`
- `decideRelationSuggestion` reject artık decided snapshot döner
- `ContinuityLedgerApi` list + approve/reject return types

### Platform
- `MeetingIntelligencePlatformConfiguration` — ledger InMemory beans + `ContinuityLedgerApi`
- `ContinuityLedgerAuthController`
- OpenAPI continuity-ledger paths

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| ContinuityLedgerApi Spring bean | ✓ |
| List events/suggestions/contradictions | ✓ |
| Accept/reject suggestion | ✓ |
| Tenant isolation | ✓ empty lists for foreign tenant |
| MEETING_READ / WRITE | ✓ |
| OpenAPI dedicated prefix | ✓ `/continuity-ledger` |
| Auto-append from approved notes | done (FAZ 27–29) |
| Brief / overdue / rebuild HTTP | deferred |
| JDBC ledger | deferred |
| confirmDelivered HTTP | deferred (delivery) |

## 5. Testler

- `ContinuityLedgerServiceTest` (module, reject return fix)
- `ContinuityLedgerAuthBindingTest` (5): accept+link, reject no-link, contradictions, foreign tenant, permission
- Regresyon: `MeetingIntelligenceAuthBindingTest`

```bash
export JAVA_HOME=$PWD/.tools/jdk-21
./mvnw -pl modules/meeting-intelligence test -Dtest='ContinuityLedgerServiceTest'
./mvnw -pl apps/platform-backend test -Dtest='ContinuityLedgerAuthBindingTest'
```

## 6. Bilinen riskler

- Note decisions ≠ ledger decisions; ürün iki modeli karıştırmamalı.
- Portal stub `/api/v1/decisions` ledger ile aynı değil.
- V184 Flyway comment “FAZ 23” — schema mevcut, JDBC runtime yok.
