# MODEL-CAPABILITY-MATRIX

**Status:** Locked for Phase 0 (target matrix)  
**Date:** 2026-07-25

Capabilities are **tags**, not vendor names. Routing matches required tags.

## Capability tags

| Tag | Meaning |
|-----|---------|
| `structured_json` | Reliable JSON conforming to schema |
| `long_context` | ≥32k context usable |
| `reasoning` | Multi-step planning quality |
| `classification` | Short-label classification |
| `summarization` | Condensing evidence narratives |
| `multilingual_tr` | Turkish quality bar |
| `low_latency` | Interactive path SLO |
| `tool_friendly` | Function/tool call style outputs |
| `vision` | Image evidence (optional later) |

## Target model slots (examples — not hard-coded in domain)

| Catalog id | Example runtime | Typical tags | Notes |
|------------|-----------------|--------------|-------|
| `local.reasoner.default` | Qwen3 / similar local reasoner | reasoning, structured_json, multilingual_tr | Default planning |
| `local.classifier.fast` | Small local instruct | classification, low_latency | Triage |
| `local.summarizer` | Mid-size local | summarization, multilingual_tr | Operator summaries |
| `local.longctx` | Long-context local | long_context, structured_json | Large evidence packs |
| `local.vision` | VL local (optional) | vision | Deferred |

Exact weights/binaries are ops concerns; catalog ids stay stable.

## Task → required capabilities

| Task type | Required tags | Prefer |
|-----------|---------------|--------|
| `plan.propose` | reasoning, structured_json, multilingual_tr | `local.reasoner.default` |
| `evidence.classify` | classification, low_latency | `local.classifier.fast` |
| `plan.summarize_for_approver` | summarization, multilingual_tr | `local.summarizer` |
| `evidence.digest_large` | long_context, structured_json | `local.longctx` |

## Evaluation gates before promoting a model

1. Structured output schema pass rate ≥ threshold on golden fixtures.
2. Evidence-id hallucination rate = 0 on fixtures (ADR-011).
3. Latency p95 within task SLO.
4. No outbound network during inference.
