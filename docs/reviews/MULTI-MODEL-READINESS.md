# MULTI-MODEL-READINESS

**Phase:** FAZ 29  
**Date:** 2026-07-25  
**Related:** [`MULTI-MODEL-ARCHITECTURE.md`](../ai/MULTI-MODEL-ARCHITECTURE.md), ADR-005, ADR-006

## Verdict

**Architecture-ready, operations-not-ready.** Routing, capability intent, and provenance exist in domain code. Production multi-model operation is blocked by InMemory catalogs/queues, incomplete orchestrator runtime API, and missing prod-profile provider lockdown.

## Capability matrix (FAZ 29)

| Capability | Status |
|------------|--------|
| Model definition / deployment registry | Domain + Flyway (`modelmanagement`) — InMemory repos at runtime |
| Task → role → deployment routing | `MultiModelRouter` / routing services present |
| Provenance on model change | **Yes** — `ModelChangeProvenance` on routing outcomes |
| Local providers (OpenAI-compat / vLLM / llama.cpp) | Present under `ai-processing` |
| Cloud LLM deny | Orchestrator egress tests deny public OpenAI/Anthropic; platform prod profile does not yet encode a full LLM egress deny-list |
| Shadow / A-B second model | Store types exist; not production-operated |
| Hard-coded Qwen role strings | Still present in routing enums/preferences (known M3 debt) |
| `MockLocalProvider` | On **main** classpath; used heavily in tests; **no prod exclusion** found |
| ai-orchestrator job API | Health/readiness (+ egress helper) — not a full inference control plane |

## Transition plan status (from MULTI-MODEL-ARCHITECTURE)

| Step | Status |
|------|--------|
| M0 Architecture lock | Done |
| M1 Catalog tables | Present (Flyway) |
| M2 Capability matrix + policies | Partial |
| M3 Replace vendor enum names with stable roles | **Required** |
| M4 Shadow second model | Planned |
| M5 Extract model-worker | Reserved / not ready |

## Stop conditions touching multi-model

- Routing **with** provenance: satisfied in domain.  
- Fake provider in prod: **risk open** (`MockLocalProvider`).  
- Cloud fallback: **policy yes / enforcement incomplete** at platform edge.  
- Unbounded AI retry queue: **open** (`InMemoryRetryQueue`).

## Go / no-go for production inference

**NO-GO** until:

1. Durable model catalog + routing decision store (JDBC).  
2. Prod profile refuses mock/fake local providers.  
3. Orchestrator serves authenticated local-only inference with allowlisted hosts.  
4. Queue depth guards wired to admission.  
5. Green AI + backend suite including routing provenance assertions.
