# FAZ-24 Continuity Ledger Rebuild Binding

**Phase:** FAZ 24  
**Date:** 2026-07-25  
**Status:** Complete (auth-bound projection rebuild from event stream)

## 1. Faz özeti

FAZ 22 rebuild’i deferred bırakmıştı. Domain `rebuildProjections` zaten event stream’den read model’i yeniden kuruyordu. Bu turda `POST /continuity-ledger/rebuild` eklendi; yanıt özet sayaçlar (event/decision/commitment/…).

## 2. Akış

```text
POST /api/v1/continuity-ledger/rebuild  (MEETING_WRITE)
        ↓
ContinuityLedgerApi.rebuildProjections(tenant)
        ↓
{ eventCount, decisionCount, commitmentCount, suggestionCount, contradictionCount, continuityCount }
```

Corrupt/cleared InMemory projection → rebuild sonrası overdue/decisions geri gelir.

## 3. Bu turda değişenler

- `ContinuityLedgerAuthController.rebuild` + `RebuildProjectionsView`
- Binding test: clear projection → rebuild restore
- OpenAPI rebuild path/schema

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Rebuild HTTP | ✓ |
| Tenant-scoped | ✓ |
| MEETING_WRITE | ✓ |
| Summary counts (not raw state dump) | ✓ |
| JDBC event store | deferred |
| Ops admin-only permission | deferred (reuses MEETING_WRITE) |

## 5. Testler

```bash
export JAVA_HOME=$PWD/.tools/jdk-21
./mvnw -pl apps/platform-backend test -Dtest='ContinuityLedgerAuthBindingTest'
```

## 6. Bilinen riskler

- Rebuild tüm tenant event’lerini memory’de oynatır; büyük stream’de pahalı olabilir.
- MEETING_WRITE yeterli; ayrı ops yetkisi yok.
