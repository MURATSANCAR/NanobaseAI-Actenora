# MULTI-MODEL-ARCHITECTURE

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## 1. Goal

Actenora must not hard-code a single LLM (including Qwen). All inference goes through **Model Gateway** with capability-based routing (ADR-005, ADR-006).

## 2. Components

```text
Planning / other BCs
        │  InferencePort
        ▼
┌───────────────────┐
│   Model Gateway   │  catalog + routing policy + jobs
└─────────┬─────────┘
          │ ModelRuntimeAdapter
          ▼
┌───────────────────┐
│ Local LLM runtime │  Ollama / vLLM / llama.cpp / …
└───────────────────┘
```

## 3. Responsibilities

| Component | Does | Does not |
|-----------|------|----------|
| Calling BC | Supply task type, evidence refs, prompt version | Choose vendor model string ad hoc |
| Model Gateway | Route, quota, timeout, record decision | Own business plans |
| Prompt Registry | Immutable prompt+schema versions | Call models |
| Runtime | Token generation | Persist business state |

## 4. Inference decision record

Every call stores: `taskType`, `policyId`, `modelId`, `promptVersionId`, `latencyMs`, `tokenUsage`, `success/failure`. Planning must reference `modelDecisionId` on `PlanProposed`.

## 5. Multi-model transition plan

| Step | Action |
|------|--------|
| M0 (now) | No code; architecture lock; forbid hard-coded model ids in future code reviews |
| M1 | Model catalog table + config-driven single default local model |
| M2 | Capability matrix + routing policies (see sibling docs) |
| M3 | Shadow route second model; compare structured-output validity |
| M4 | Task-type weighted routing in production |
| M5 | Extract Model Gateway service if GPU isolation required |

## 6. Hard-coded Qwen — current inventory

**Repository scan result (Phase 0):** no application source exists. **Qwen hard-coded call sites: none (N/A).**

Risk watch list for Phase 1+ (prevent):

| Location risk | Mitigation |
|---------------|------------|
| `application.yml` default `qwen2.5` only | Allow, but behind catalog id — not domain imports |
| Prompt templates naming “Qwen” | Brand as NanobaseAI; model id only in modelgw |
| Test fixtures assuming one tokenizer | Use capability tags, not model name asserts |
| Docker Compose service named `qwen` | Prefer `llm-runtime` |

## 7. Local-only

Default profiles deny egress to hosted LLM APIs. Break-glass requires explicit non-default profile + security sign-off (ADR-005).
