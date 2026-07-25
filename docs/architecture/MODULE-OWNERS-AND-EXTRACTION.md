# MODULE-OWNERS-AND-EXTRACTION

**Status:** Phase 3 (FAZ 3) — locked with Spring Modulith + ArchUnit  
**Date:** 2026-07-25  
**Package root:** `com.nanobaseai.actenora.<context>`  
**Maven groupId:** `ai.nanobase.actenora`

## Bounded context diagram

```text
                    ┌──────────────┐
                    │ sharedkernel │  (OPEN — types/ports only)
                    └──────┬───────┘
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
   ┌──────────┐      ┌──────────┐      ┌──────────┐
   │ identity │─────▶│  tenant  │─────▶│  policy  │
   └──────────┘      └────┬─────┘      └────┬─────┘
                          │                 │
              ┌───────────┼───────────┐     │
              ▼           ▼           ▼     ▼
   ┌────────────────────┐ ┌────────┐ ┌───────────────┐
   │ microsoftconnection│ │meeting │ │modelmanagement│
   └─────────┬──────────┘ └───┬────┘ └───────┬───────┘
             │                │              │
             └───────▶ meeting ──────┐       │
                          │          │       │
                          ▼          ▼       ▼
                     ┌──────────┐  ┌──────────────┐
                     │transcript│  │ aiprocessing │
                     └────┬─────┘  └──────┬───────┘
                          │               │
                          └───────┬───────┘
                                  ▼
                       ┌────────────────────┐
                       │meetingintelligence │
                       └────────────────────┘

   ┌──────────┐   ┌──────────┐   ┌──────────┐
   │ approval │◀──┤  policy  │   │ template │
   └────┬─────┘   └──────────┘   └────┬─────┘
        │                             │
        └──────────▶ delivery ◀───────┘

   ┌───────┐     ┌────────────┐
   │ audit │     │ operations │
   └───────┘     └────────────┘
   (events only)  (tenant :: api)
```

Generated Modulith PlantUML is written under `apps/platform-backend/target/spring-modulith-docs/` when
`ModulithArchitectureTest` runs.

## Module owners & extraction readiness

| Module | Owner | Extraction readiness | Notes |
|--------|-------|----------------------|-------|
| sharedkernel | Platform | **never** | IDs, clocks, errors, messaging primitives — never a service |
| identity | Platform Security | P3 | Extract when multi-product IdP is required |
| tenant | Platform Security | P3 | Keep with identity until tenancy scale pain is clear |
| policy | Platform Governance | P3 | Quota / SLA / allowlists; compliance boundary |
| microsoftconnection | Integrations | P2 | Graph credential isolation candidate |
| meeting | Meetings | P2 | Core aggregate; extract with transcript only if needed |
| transcript | Meetings | P1 | I/O-heavy → `services/transcript-worker` |
| modelmanagement | AI Platform | P1 | Pairs with `services/model-worker` |
| aiprocessing | AI Platform | P1 | Inference scale independent of intelligence store |
| meetingintelligence | Meetings | P2 | Insights persistence; keep after AI processing split |
| approval | Governance | P2 | Human gate before side effects |
| template | Delivery | P3 | Extract with `services/document-renderer` |
| delivery | Delivery | P1 | Side-effect adapters → `services/delivery-worker` |
| audit | Platform Governance | P3 | Append-only; SIEM export later |
| operations | Platform Ops | P3 | Ops tooling; keep in monolith longest |

## Public API convention

- Each BC publishes **only** `com.nanobaseai.actenora.<context>.api` (`@NamedInterface("api")`).
- Domain / application / infrastructure packages are **internal** (Modulith + ArchUnit).
- Cross-module calls use API façades, opaque IDs, or integration events — never repositories or entities.

## Enforcement

| Gate | Location |
|------|----------|
| Spring Modulith `verify()` | `ModulithArchitectureTest` |
| ArchUnit suite | `ModularMonolithArchUnitTest` |
| Dependency graph | Modulith `Documenter` output |
| Schema ownership | Flyway under `db/migration/<schema>/` per module |
