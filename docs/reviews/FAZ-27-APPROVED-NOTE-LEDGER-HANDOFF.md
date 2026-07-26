# FAZ-27 Approved Note → Continuity Ledger Handoff

**Phase:** FAZ 27  
**Date:** 2026-07-25  
**Status:** Complete (approved current-version artifacts append to the ledger)

## 1. Faz özeti

FAZ 21 ve FAZ 25 Continuity Ledger yüzeylerini bağladı ancak note approval ile ledger arasında otomatik handoff yoktu. Bu turda yalnızca insan onayı `GRANTED` olduğunda onaylanan note version'a ait decision ve commitment kayıtları tenant-scoped olarak ledger event stream'ine ekleniyor.

FAZ 26 numarası mevcut transcript service extraction çalışmasına ait olduğu için bu çalışma FAZ 27 olarak kaydedildi.

## 2. Akış

```text
ApprovalApi.decide → GRANTED
        ↓
MeetingNote status → APPROVED
        ↓
ApprovedNoteLedgerPort
        ↓
current note-version Decision / Commitment
        ↓
DECISION_RECORDED / COMMITMENT_RECORDED
```

`DENIED` ve `CHANGES_REQUESTED` sonuçları ledger handoff üretmez.

## 3. Değişenler

- `ApprovedNoteLedgerPort` — approval uygulama katmanı çıkış portu
- `ApprovedNoteLedgerAdapter` — current-version artifact filtreleme ve ledger append
- `MeetingNoteApprovalService` — yalnızca `GRANTED` sonrası handoff
- Platform configuration — adapter ve approval service wiring
- `ApprovalAuthBindingTest` — approved decision/commitment event doğrulaması

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|-------|
| Sadece insan onayı sonrası append | ✓ |
| Tenant-scoped repository reads | ✓ |
| Yalnızca onaylanan note version | ✓ |
| Meeting occurrence bağını koruma | ✓ |
| Decision + commitment append | ✓ |
| Rejection/change request skip | ✓ (status guard) |
| Transactional outbox / retry idempotency | done (FAZ 28–29) |
| Artifact source ID provenance | done (FAZ 28) |
| Commitment due date aktarımı | deferred (source modelde yok) |

## 5. Testler

```bash
export JAVA_HOME=$PWD/.tools/jdk-21
./mvnw -pl apps/platform-backend -am test \
  -Dtest='ApprovalAuthBindingTest,ContinuityLedgerAuthBindingTest' \
  -Dsurefire.failIfNoSpecifiedTests=false
./mvnw -pl modules/meeting-intelligence test \
  -Dtest='MeetingNoteApprovalWorkflowTest'
```

## 6. Bilinen riskler

- Approval, note save ve ledger append tek kalıcı transaction/outbox içinde değil. Kalıcı adapter aşamasında retry-safe outbox gerekir.
- Ledger event payload'ı kaynak decision/commitment ID'sini taşımıyor; traceability note ID seviyesinde.
- Source `Commitment` modelinde due date bulunmadığından ledger commitment due date'i boş oluşturulur.
