# MODEL-FAILOVER-REPORT

**Phase:** 28 — Load, resilience & failure tests  
**Date:** 2026-07-25  
**Raw capture:** [`artifacts/phase-28/surefire-faz28.log`](../../artifacts/phase-28/surefire-faz28.log)  
**Related:** ADR-005, ADR-006 · `MultiModelRouter` · `ModelChangeProvenance`

## 1. Executive verdict

Local multi-model routing correctly **fails over from a down primary Qwen deployment to the secondary deployment**, records **auditable provenance**, and when **all models are unavailable** escalates **RETRY_QUEUE → MANUAL_REVIEW**. Fallback provenance fields match the routing decision id; quality downgrade flags behave per tenant/critical policy.

## 2. Scenarios

| Scenario | Test | Result |
|----------|------|--------|
| Single Qwen deployment down → 2nd deployment | `singleQwenDeploymentDown_fallsBackToSecondDeploymentWithProvenance` | PASS |
| Provenance correctness | Same + existing `MultiModelRouterTest.provenanceCorrectnessOnFallback` | PASS |
| All models unavailable | `allModelsUnavailable_goesRetryQueueThenManualReview` | PASS |
| Alternate model / quality downgrade | `MultiModelRouterTest.alternateModelAllowedWithQualityDowngradeFlag` | PASS (suite) |
| Critical forbids quality downgrade | `MultiModelRouterTest.criticalJobForbidsQualityDowngrade` | PASS (suite) |
| Worker drain on restart | `workerRestart_drainRejectsNewWorkThenQuiesces` | PASS |
| Invalid JSON bounded repair | `invalidJson_repairBoundedThenFails` | PASS |
| Context overflow pre-flight | `contextOverflow_rejectedBeforeModelCall` | PASS |

## 3. Fallback chain (observed)

```text
PRIMARY
  → SAME_MODEL_OTHER_DEPLOYMENT   (secondary Qwen deployment)
    → ALTERNATE_LOCAL_MODEL       (if tenant allows + not critical-forbidden)
      → RETRY_QUEUE
        → MANUAL_REVIEW
```

| Step | Provenance |
|------|------------|
| SAME_MODEL_OTHER_DEPLOYMENT | `fromDeploymentId` = primary, `toDeploymentId` = secondary, `qualityDowngraded=false` |
| ALTERNATE_LOCAL_MODEL | `qualityDowngraded=true` when policy allows |
| RETRY_QUEUE / MANUAL_REVIEW | No production route; job id retained for operator escalation |

## 4. Acceptance criteria

| Criterion | Met? |
|-----------|------|
| Fallback provenance doğru | Yes — `routingDecisionId`, from/to deployment ids, step, quality flag |
| All-unavailable path safe | Yes — retry queue then manual review; no silent drop |
| Critical starvation yok | Yes — scheduling prefers CRITICAL (LOAD report) |
| Veri kaybı / duplicate yok | Yes — routing decisions + provenance persisted in harness stores |

## 5. Inventory notes

| Item | Status |
|------|--------|
| Hard-coded Qwen in domain call sites | Catalog keys only via `DefaultModelRoleBootstrap` (not ad-hoc domain imports) |
| Decision store | In-memory for FAZ 28; JDBC port reserved |

## 6. How to re-run

```bash
./mvnw -B -pl modules/ai-processing -am test \
  -Dtest='com.nanobaseai.actenora.aiprocessing.faz28.**' \
  -Dsurefire.failIfNoSpecifiedTests=false
# or full phase:
make faz28
```
