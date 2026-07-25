# SERVICE-DECOMPOSITION-MAP

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## 1. Principle

Ship as modular monolith. Extract only when a module meets objective criteria (see playbook). This map names **candidates**, not a commitment to split.

## 2. Extraction candidates

| Priority | Candidate service | Source BC(s) | Why it may split | Coupling risk |
|----------|-------------------|--------------|------------------|---------------|
| P1 | `actenora-model-gateway` | modelgw, prompt (read) | GPU/CPU isolation, independent scale, blast radius of model upgrades | Medium — needs stable Inference API |
| P1 | `actenora-delivery` | delivery | Credential isolation for external systems; different SLOs | Medium — approval token contract |
| P2 | `actenora-workflow` | workflow | Timer/scale characteristics; long-running load | High — many event peers |
| P2 | `actenora-evidence` | evidence, artifact | Storage-heavy I/O | Medium |
| P3 | `actenora-audit` | audit | Append-only, retention, SIEM export | Low |
| P3 | `actenora-approval` | approval | Compliance boundary | Medium |
| Later | `actenora-notify` | notify | Channel fan-out | Low |
| Keep last | identity, workspace, planning, execution, knowledge, case | — | Core product loop | — |

## 3. Decomposition sketch

```text
TODAY (target Phase 1+)
┌────────────────────────────────────┐
│         Actenora Monolith          │
│  identity workspace evidence ...   │
│  modelgw workflow approval delivery│
└────────────────────────────────────┘

FUTURE (selective)
┌──────────────┐  ┌─────────────┐  ┌──────────────┐
│ monolith     │  │ model-gw    │  │ delivery     │
│ core BCs     │  │ + GPU nodes │  │ + secrets    │
└──────────────┘  └─────────────┘  └──────────────┘
        │ events via RabbitMQ + contracts │
```

## 4. Pre-extraction contract checklist

Before any split:

1. Public commands/events versioned in `EVENT-CATALOG.md`.
2. No shared JPA entities.
3. Schema already isolated.
4. Outbox/inbox proven in monolith.
5. AuthN/Z token or mTLS story for service-to-service.
6. Observability (trace id) across broker.

## 5. Non-candidates (do not extract early)

- `planning` alone without modelgw — thrashing chatter.
- `shared-kernel` — never a service.
- Partial table extraction without owning schema move.
