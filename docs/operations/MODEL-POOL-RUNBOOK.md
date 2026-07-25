# MODEL-POOL-RUNBOOK

**Owner:** Platform / AI on-call  
**Scope:** Local model pool behind model-management + ai-processing + ai-orchestrator  
**Related:** [`SCALING-STRATEGY.md`](SCALING-STRATEGY.md), [`MULTI-MODEL-ARCHITECTURE.md`](../ai/MULTI-MODEL-ARCHITECTURE.md)

## Purpose

Operate a **local-only** inference pool (vLLM / llama.cpp / OpenAI-compatible local gateway). Cloud LLM endpoints are denied by policy (ADR-005).

## Topology (target)

```text
platform-backend (admission + routing decision)
        │ events / job refs
        ▼
ai-orchestrator  ──HTTPS──► local LLM hosts (allowlisted)
model-management catalog / deployments
```

## Preconditions

- [ ] `ACTENORA_ENV=production`
- [ ] `ACTENORA_AI_EGRESS_DENY_PUBLIC=true`
- [ ] Explicit `ACTENORA_AI_EGRESS_ALLOWLIST` for corp LLM hosts only
- [ ] No `MockLocalProvider` bean in prod classpath/profile
- [ ] Deployment rows in `modelmanagement` point at local served model ids
- [ ] Queue depth metrics + `QueueDepthGuard` thresholds configured

## Routine operations

### Add a model deployment

1. Register definition + capabilities in model-management control plane.  
2. Create deployment with base URL on private network.  
3. Allowlist per tenant if required.  
4. Smoke: route a non-production tenant task; verify provenance fields on routing decision.  
5. Enable shadow traffic before primary cutover.

### Drain / replace a hot model

1. Mark deployment `DRAINING` (reject new admissions; finish in-flight).  
2. Watch attempt backlog and DLQ.  
3. Promote fallback deployment via routing policy (provenance must record change reason).  
4. Decommission old host only after zero in-flight attempts.

### Scale pool

1. Prefer horizontal local replicas behind an internal LB.  
2. Raise admission capacity only with queue depth headroom.  
3. Never bypass approval or quality gates to “catch up”.

## Alerts

| Signal | Action |
|--------|--------|
| Inference queue depth > threshold | Shed new AI admissions; scale workers/GPUs |
| Egress deny spikes | Suspected cloud URL misconfig — page security |
| High timeout rate on one deployment | Fail over via routing; quarantine deployment |
| Provenance missing on decisions | **Stop routing changes**; treat as release blocker |

## Forbidden actions

- Pointing prod at `api.openai.com` / Anthropic / other public LLM APIs  
- Enabling mock providers in prod  
- Logging raw prompts, responses, or transcript text  
- Hard-coding a single vendor model id in domain call sites (use role → deployment)

## Rollback

See [`ROLLBACK-RUNBOOK.md`](ROLLBACK-RUNBOOK.md) §Model pool. Revert deployment allowlist / routing preference to last known-good deployment id; keep provenance of the rollback decision.
