# FAZ-28 Idempotent Approved-Note Ledger Provenance

**Phase:** FAZ 28  
**Date:** 2026-07-25  
**Status:** Complete (source artifact IDs + idempotent append)

## 1. Faz özeti

FAZ 27 approved-note → ledger handoff random ledger aggregate ID üretiyordu; retry duplicate event riski vardı ve kaynak Decision/Commitment izi kayboluyordu. Bu turda note artifact ID’leri ledger aggregate ID olarak kullanılıyor ve aynı ID ile ikinci kayıt no-op.

## 2. Akış

```text
Approved note Decision.id / Commitment.id
        ↓
ContinuityLedgerApi.record*(…, sourceId, …)
        ↓
projection already has sourceId? → return existing (no event)
        ↓ else
DECISION_RECORDED / COMMITMENT_RECORDED (aggregateId = sourceId)
```

## 3. Değişenler

- `ContinuityLedgerService` — decision/commitment record idempotent; commitment stable-id overload
- `ContinuityLedgerApi` — source-id overload’ları
- `ApprovedNoteLedgerAdapter` — `decision.id()` / `commitment.id()` geçirir
- Tests: service idempotency + binding provenance/retry

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Artifact source ID = ledger aggregate ID | ✓ |
| Double append does not duplicate events | ✓ |
| Manual HTTP record (null id) still works | ✓ |
| Transactional outbox | done (FAZ 29) |
| Commitment due date from note model | deferred (modelde yok) |
| JDBC ledger | deferred |

## 5. Testler

```bash
export JAVA_HOME=$PWD/.tools/jdk-21
./mvnw -pl apps/platform-backend,modules/meeting-intelligence -am test \
  -Dtest='ApprovalAuthBindingTest,ContinuityLedgerServiceTest,ContinuityLedgerAuthBindingTest' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 6. Bilinen riskler

- Idempotency projection state’e bağlı; corrupt projection + retry yeniden event yazabilir (rebuild sonrası düzelir).
- Outbox yok; process crash approval sonrası / append öncesi hâlâ gap bırakabilir.
