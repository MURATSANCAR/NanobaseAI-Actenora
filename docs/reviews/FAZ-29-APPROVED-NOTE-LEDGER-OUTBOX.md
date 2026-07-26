# FAZ-29 Approved-Note Ledger Outbox Handoff

**Phase:** FAZ 29  
**Date:** 2026-07-25  
**Status:** Complete (approval GRANTED → transactional outbox → ledger projection)

## 1. Faz özeti

FAZ 27/28 approval sonrası ledger’a senkron yazıyordu; crash gap’i vardı. Bu turda handoff transactional outbox üzerinden asenkronlaştı: approval yalnızca `meetingintelligence.NoteApprovedForLedger.v1` enqueue eder; relay + inbox consumer continuity ledger’a yazar (FAZ 28 source-id idempotency korunur).

## 2. Akış

```text
Approval GRANTED
        ↓
OutboxApprovedNoteLedgerAdapter.enqueue
        ↓
outbox PENDING
        ↓
relay.publishDueBatch / polling relay
        ↓
inbox IdempotentEventConsumer
        ↓
ApprovedNoteLedgerAdapter (sync writer)
        ↓
DECISION_RECORDED / COMMITMENT_RECORDED
```

## 3. Değişenler

- `MeetingIntelligenceIntegrationEvents.NOTE_APPROVED_FOR_LEDGER`
- Event contract schema JSON
- `OutboxApprovedNoteLedgerAdapter` — `@Primary` ApprovedNoteLedgerPort
- `ApprovedNoteLedgerAdapter` — sync writer bean
- `NoteApprovedForLedgerHandler` + EventBackbone fan-out subscribe
- `ApprovalAuthBindingTest` — outbox → relay → ledger + inbox duplicate
- EVENT-CATALOG satırı

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Approval does not sync-write ledger | ✓ |
| Outbox enqueue on GRANTED | ✓ |
| Relay/inbox delivers to ledger writer | ✓ |
| Inbox duplicate is no-op | ✓ |
| Source-id ledger idempotency (FAZ 28) | ✓ |
| Event contract schema | ✓ |
| JDBC outbox / Rabbit transport | deferred |

## 5. Testler

```bash
export JAVA_HOME=$PWD/.tools/jdk-21
./mvnw -pl apps/platform-backend,modules/meeting-intelligence -am test \
  -Dtest='ApprovalAuthBindingTest,ContinuityLedgerServiceTest,MeetingTranscriptEventChoreographyTest' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 6. Bilinen riskler

- InMemory outbox + fan-out; production JDBC/Rabbit hâlâ yok.
- Approval DB transaction ile outbox aynı JDBC transaction’a bağlı değil (InMemory composition).
- Handler sync writer’ı yanlışlıkla outbox port’a bağlanırsa enqueue döngüsü oluşur — bean tipi `ApprovedNoteLedgerAdapter` ile sabitlendi.
