# MODULAR-MONOLITH-BOUNDARIES

**Status:** Locked for Phase 3 (FAZ 3) — enforced in code  
**Date:** 2026-07-25

## 1. Layering (every bounded context module)

```text
api            → HTTP controllers, message consumers, public façades & integration events
application    → use cases, transaction boundaries, ports
domain         → aggregates, domain services, domain events (pure)
infrastructure → JPA, RabbitMQ, MinIO, Graph HTTP, clocks (adapters out)
```

### Dependency rule

```text
api → application → domain
infrastructure → application/domain (implements ports)
domain → ∅ (no Spring, JPA, RabbitMQ, Graph client, MinIO, HTTP)
```

## 2. Coding rules (enforced)

| Rule | Enforcement |
|------|-------------|
| Domain must not depend on Spring | ArchUnit |
| No cross-module repository injection | ArchUnit + Modulith |
| No cross-module entity imports | ArchUnit + Modulith |
| Shared-kernel: no `*Service` / `@Service` | ArchUnit |
| No infrastructure stereotypes in `domain` | ArchUnit |
| Internal packages inaccessible cross-module | Modulith `verify()` + ArchUnit |
| No cyclic BC dependencies | Modulith + ArchUnit slices |
| Meeting ↛ TranscriptRepository | ArchUnit |
| Transcript ↛ MeetingEntity | ArchUnit |
| AI Processing ↛ intelligence persistence | ArchUnit |
| Delivery ↛ approval.domain | ArchUnit |

## 3. Inter-module communication

| Need | Mechanism |
|------|-----------|
| Same-process query | Application port / façade in owner `api` |
| State change in another BC | Integration event via outbox |
| Long-running | Choreography; no distributed XA |

**Never:** direct repository access across modules.

## 4. Package layout

```text
modules/<kebab-name>/src/main/java/com/nanobaseai/actenora/<context>/
  package-info.java          # @ApplicationModule + allowedDependencies
  api/package-info.java      # @NamedInterface("api")
  api/...
  domain/...
  application/...
  infrastructure/...
  src/main/resources/db/migration/<schema>/Vxxx__....sql
```

Shared kernel: `modules/shared-kernel` → `com.nanobaseai.actenora.sharedkernel` (`Type.OPEN`).

## 5. Transaction boundaries

- One use case = one transaction **within one schema**.
- Multi-aggregate consistency across BCs = **choreography via outbox**.

## 6. Verification commands

```bash
./mvnw -pl apps/platform-backend -am install -Dmaven.test.skip=true
./mvnw -pl apps/platform-backend test -Dtest=ModulithArchitectureTest,ModularMonolithArchUnitTest
```

Modulith diagrams: `apps/platform-backend/target/spring-modulith-docs/` (also mirrored under `docs/architecture/modulith/`).
