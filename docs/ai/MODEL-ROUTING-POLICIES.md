# MODEL-ROUTING-POLICIES

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## 1. Policy object

```text
RoutingPolicy {
  id, version,
  taskType,
  requiredCapabilities[],
  candidateModelIds[],   // catalog ids
  selection: FIRST_FIT | WEIGHTED | SHADOW,
  timeoutMs, maxRetries,
  fallbackModelId?,
  denyCloud: true
}
```

## 2. Selection strategies

| Strategy | Behavior |
|----------|----------|
| `FIRST_FIT` | First healthy candidate matching tags |
| `WEIGHTED` | Weighted random among healthy candidates |
| `SHADOW` | Primary serves; shadow model scored offline |

## 3. Health & exclusion

- Consecutive failures → temporary circuit-open for that model id.
- Fallback only to another **local** catalog model.
- Exhaustion → `InferenceFailed` event; workflow policy decides.

## 4. What callers pass

Callers pass `taskType` + prompt version + payload refs. They **must not** pass raw vendor model strings (except ops admin APIs).

## 5. Observability

Emit `routing_decisions` row: policy version, candidates considered, chosen model, reject reasons.

## 6. Default deny

Any policy with `denyCloud: false` requires security exception ticket; not shippable in default compose profile.
