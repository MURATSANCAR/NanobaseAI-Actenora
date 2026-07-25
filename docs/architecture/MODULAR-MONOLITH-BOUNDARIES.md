# MODULAR-MONOLITH-BOUNDARIES

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## 1. Layering (every bounded context module)

```text
api            → HTTP controllers, message consumers (adapters in)
application    → use cases, transaction boundaries, ports
domain         → aggregates, domain services, domain events (pure)
infrastructure → JPA, RabbitMQ, MinIO, LLM HTTP, clocks (adapters out)
```

### Dependency rule

```text
api → application → domain
infrastructure → application/domain (implements ports)
domain → ∅ (no Spring, JPA, RabbitMQ, Graph client, MinIO, HTTP)
```

## 2. Coding rules (enforced later via ArchUnit)

| Rule | Enforcement |
|------|-------------|
| Constructor injection only | ArchUnit + review |
| No field injection | ArchUnit |
| No JPA entity as API response | ArchUnit + DTO records |
| DTOs prefer Java `record` | Review |
| No cross-module repository calls | ArchUnit |
| No cross-module entity types | ADR-012 / ArchUnit |
| Shared-kernel: no business services | ArchUnit package rules |

## 3. Inter-module communication

| Need | Mechanism |
|------|-----------|
| Same-process query | Application port / façade interface published by owner module |
| State change in another BC | Command message or domain event via outbox |
| Long-running | Workflow context owns instance; others react |

**Never:** direct repository access across modules.

## 4. Package layout (target)

```text
actenora/
  apps/
    api/                 # Spring Boot API starter
    worker/              # Spring Boot worker starter
  modules/
    identity/
    workspace/
    evidence/
    ...
  shared/
    shared-kernel/
    archunit-tests/
```

Exact Gradle/Maven multi-module layout is Phase 1; this locks **logical** boundaries.

## 5. Transaction boundaries

- One use case = one transaction **within one schema**.
- Multi-aggregate consistency across BCs = **choreography via outbox**, not distributed XA.

## 6. Testing pyramid for boundaries

| Test type | Purpose |
|-----------|---------|
| Unit | Domain & application pure logic |
| Integration | Module + its schema + broker testcontainers |
| Architecture | ArchUnit module/layer rules |
| E2E | Happy-path workflow with approval gate |
| Load | Later; not Phase 0 |
