# LOAD-TEST-REPORT

**Phase:** 28 — Load, resilience & failure tests  
**Date:** 2026-07-25  
**Raw capture:** [`artifacts/phase-28/surefire-faz28.log`](../../artifacts/phase-28/surefire-faz28.log) · [`summary.txt`](../../artifacts/phase-28/summary.txt)

## 1. Executive verdict

In-process load harnesses confirm the platform survives **daily 30 meetings**, **~40 minute average duration**, and a **100-meeting same-hour burst** without data loss or duplicate business records. AI job burst (100) admits under tenant queue ceilings, measures SLA breaches, and keeps critical work ahead of bulk. Queues are bounded via `QueueDepthGuard` (reject-publish style soft backpressure).

## 2. Scenarios executed

| Scenario | Harness | Result |
|----------|---------|--------|
| Daily 30 meetings | `DailyMeetingLoadScenarioTest.dailyThirtyMeetings_*` | PASS |
| Average 40 minutes | Same (duration assert = 40.0 min) | PASS |
| 100 meetings end same hour | `hundredMeetingsEndingSameHour_*` | PASS |
| 100 AI-job burst | `BurstFairnessAndSlaLoadScenarioTest.hundredJobBurst_*` | PASS |
| Critical vs bulk fairness | `criticalVsBulk_fairnessPreventsCriticalStarvation` | PASS |
| SLA breach measurement | `delayedBulk_recordsSlaBreach` + burst tracker | PASS |
| Queue backlog bound | `QueueDepthGuard` in burst + messaging tests | PASS |
| Tenant quota under load | `QuotaUnderLoadScenarioTest` (31st meeting → 429) | PASS |

## 3. Load profile

| Parameter | Value |
|-----------|-------|
| Meetings / day | 30 |
| Mean duration | 40 minutes |
| Burst | 100 occurrences with `scheduledEndAt` in the same hour |
| AI jobs in burst | 100 (10 CRITICAL, mix NORMAL/BULK across 2 tenants) |
| Queue max depth (guard) | 120 for AI jobs; 50 for messaging backlog demo |

## 4. Observations

| Metric | Observed |
|--------|----------|
| Meeting record loss | 0 |
| Duplicate graph identity on replay | Rejected (`DuplicateGraphIdentityException`) |
| AI jobs admitted in burst | 100 / 100 |
| Critical starvation under 40 BULK | None — CRITICAL claimed first |
| CRITICAL SLA breaches (target 5m) | 0 when completed within 2m |
| BULK SLA breach (target 240m, completed at +5h) | Recorded by `SlaBreachTracker` |
| Uncontrolled queue growth | Prevented (`tryAdmit` rejects past max) |

## 5. Acceptance criteria

| Criterion | Met? |
|-----------|------|
| Veri kaybı yok | Yes |
| Duplicate business record yok | Yes (meeting graph id + transcript content-hash) |
| Kuyruklar kontrolsüz büyümüyor | Yes (`QueueDepthGuard` / tenant queue 4× concurrency) |
| SLA breach ölçülüyor | Yes (`SlaBreachTracker`) |
| Critical starvation yok | Yes |

## 6. Gaps / next hardening

| Gap | Note |
|-----|------|
| Live Docker compose soak | Not required for FAZ 28 unit harness; FAZ 29 E2E may add |
| Broker-backed depth metrics | Rabbit `overflow: reject-publish` exists in definitions; adapter still in-memory for these runs |

## 7. How to re-run

```bash
make faz28
# or
./scripts/run-faz28-tests
```
