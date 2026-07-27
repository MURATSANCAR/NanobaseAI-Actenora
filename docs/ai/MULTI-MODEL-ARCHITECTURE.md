# MULTI-MODEL-ARCHITECTURE

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## Goal

Inference goes through **model-management** (catalog/deployments) + **ai-processing** routing + **ai-orchestrator** runtime — never hard-coded vendor strings in domain forever.

## Components

```text
meeting-intelligence / callers
        │
        ▼
   ai-processing (task → logical role → policy)
        │
        ▼
   model-management (model_definition / capability / deployment)
        │
        ▼
   ai-orchestrator + local LLM runtime
```

## Multi-model transition plan

| Step | Action | Status |
|------|--------|--------|
| M0 | Architecture lock; inventory hard-codes | **Done (this doc)** |
| M1 | Catalog tables (`model_definition` et al.) | Present in Flyway |
| M2 | Capability matrix + routing policies | Partial (enums exist; not fully catalog-driven) |
| M3 | Replace domain enum vendor names with stable role ids | **Required** |
| M4 | Shadow second model; compare schema validity | Planned |
| M5 | Extract `services/model-worker` if GPU isolation needed | Reserved |

## Hard-coded Qwen inventory (non-docs, Phase 0 scan)

| Location | Finding |
|----------|---------|
| `modules/ai-processing/.../routing/ModelRole.java` | Enum constant **`QWEN27_FINAL`** |
| `.../ValidationModelPreference.java` | **`QWEN27_FINAL`** |
| `.../TenantRoutingPolicy.java` | Default preference `QWEN27_FINAL` |
| `.../TaskRoleMapping.java` | Maps final-note tasks → `ModelRole.QWEN27_FINAL` |
| `infrastructure/compose/docker-compose.yml` | Env `QWEN_BASE_URL` |
| `.env.example` | `LLM_BASE_URL` / `ACTENORA_AI_PROVIDER_BASE_URL` → OpenAI-compatible runtime (`host.docker.internal:8010` llama-server; Ollama `:11434` alternative). Backend must **not** use ai-orchestrator `:8000` (health-only). |

**Local llama-server:** `scripts/server/restore-meeting-35b-llm.sh` (alias must match `ACTENORA_AI_PROVIDER_*_SERVED_MODEL_ID`).

**Remediation (Phase 1+):** rename role to e.g. `FINAL_NOTE_PRIMARY`; bind physical Qwen (or other) only in `modelmanagement.model_definition.served_model_id` / deployment rows.

## Local-only

Default profiles deny hosted LLM APIs (ADR-005).
