# FAZ-25 Continuity Ledger Commitment Binding

**Phase:** FAZ 25  
**Date:** 2026-07-25  
**Status:** Complete (record / confirm / reject commitment HTTP)

## 1. Faz özeti

FAZ 22 commitment kaydını testlerde domain API ile seed ediyordu; HTTP yoktu. Domain `recordCommitment` / `confirmCommitment` / `rejectCommitment` zaten olgundu. Bu turda auth-bound yüzey eklendi — yeni domain yok.

## 2. Akış

```text
POST /api/v1/continuity-ledger/commitments
        ↓  PENDING_CONFIRMATION (+ overdue derived)
POST .../commitments/{id}/confirm | reject
        ↓
CONFIRMED | REJECTED
```

Geçersiz transition → `INVALID_COMMITMENT_TRANSITION` (409).  
Reject overdue listeden düşer; confirm past-due hâlâ overdue sayılır (domain invariant).

## 3. Bu turda değişenler

- `ContinuityLedgerAuthController` — record / confirm / reject
- Exception mapping: `INVALID_COMMITMENT_TRANSITION` → 409
- Binding test: happy path + double-decide conflict
- OpenAPI commitment paths + `RecordContinuityCommitment`

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Record commitment HTTP | ✓ |
| Confirm / reject HTTP | ✓ |
| MEETING_WRITE | ✓ |
| Actor = principal.userId | ✓ |
| OpenAPI | ✓ |
| Auto-append from approved notes | done (FAZ 27–29) |
| JDBC ledger | deferred |

## 5. Testler

```bash
export JAVA_HOME=$PWD/.tools/jdk-21
./mvnw -pl apps/platform-backend test -Dtest='ContinuityLedgerAuthBindingTest'
```

## 6. Bilinen riskler

- `noteId` body’de opsiyonel; yoksa random UUID — note bağını zayıf tutar.
- Confirm ≠ fulfilled; overdue hâlâ CONFIRMED past-due için true olabilir.
