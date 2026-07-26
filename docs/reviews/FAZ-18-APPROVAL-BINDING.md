# FAZ-18 Approval Binding

**Phase:** FAZ 18  
**Date:** 2026-07-25  
**Status:** Complete (InMemory Approval + note sync + auth HTTP)

## 1. Faz özeti

Approval BC (workflow, immutability, disputes, Flyway) ve `MeetingNoteApprovalService` zaten vardı; platform’da bean/HTTP yoktu, DRAFT notlar approval’a hiç açılmıyordu. Bu turda Approval InMemory composition root’a bağlandı, auth-bound submit/decide/get eklendi ve kararlar meeting-note version status ile senkronize ediliyor. `ApprovalApi` bean’i Delivery’nin gerçek `ApprovalApiNoteApprovalGate` yolunu da aktive eder.

## 2. Akış

```text
MeetingNote DRAFT (FAZ 16/17 mapAiCandidates veya createDraft)
        ↓
POST /meeting-notes/{noteId}/submit-for-approval   (MEETING_WRITE)
        ↓
Approval PENDING + note version PENDING_APPROVAL
        ↓
POST /approvals/{approvalId}/decide               (APPROVAL_DECIDE)
        ↓
Approval GRANTED|DENIED|CHANGES_REQUESTED
  + note version APPROVED|REJECTED|CHANGES_REQUESTED
        ↓
ApprovalApi.isGrantedForSubject → Delivery gate
```

Actor / approver anahtarı: `principal.userId().toString()`.

## 3. Bu turda değişenler

### Approval API
- `ApprovalRequestView` + `ApprovalApi.get(...)`
- `MeetingNoteApprovalService.decideByApprovalId` — subject = `MEETING_NOTE_VERSION` → note resolve

### Platform
- `ApprovalPlatformConfiguration` — InMemory repos, AuditApi bridge, `ApprovalApi`, `MeetingNoteApprovalService`
- `ApprovalAuthController` — submit / get / decide

### Contract
- OpenAPI: domain status enum, `REQUEST_CHANGES`, `expected*Version`, submit/get paths

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| `ApprovalApi` Spring bean | ✓ |
| Submit DRAFT → PENDING | ✓ |
| Decide syncs note version | ✓ |
| Approver identity enforced | ✓ |
| `APPROVAL_DECIDE` permission | ✓ |
| Tenant isolation on get | ✓ |
| Delivery gate via `isGrantedForSubject` | ✓ (API; delivery E2E deferred) |
| OpenAPI status alignment | ✓ partial (portal TS deferred) |
| Quality-gate override → note → approval | deferred (FAZ 17) |
| JDBC approval repos | deferred |
| Auto-submit after AI handoff | deferred (explicit HTTP) |

## 5. Testler

- `ApprovalAuthBindingTest` (4): submit+approve sync, permission deny, wrong approver, foreign tenant
- Module regresyon: `approval`, `meeting-intelligence` (`MeetingNoteApprovalWorkflowTest`), `delivery`

```bash
export JAVA_HOME=$PWD/.tools/jdk-21
./mvnw -pl modules/approval,modules/meeting-intelligence,modules/delivery test
./mvnw -pl apps/platform-backend test -Dtest='ApprovalAuthBindingTest,MeetingIntelligenceAuthBindingTest'
```

## 6. Bilinen riskler

- Submit hâlâ manuel HTTP; AI handoff otomatik approval açmaz.
- Override sonrası note üretimi yok → approval yolu da yok.
- Portal `types.ts` henüz GRANTED/DENIED’e güncellenmedi.
- InMemory only.
