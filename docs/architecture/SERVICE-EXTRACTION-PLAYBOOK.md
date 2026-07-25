# SERVICE-EXTRACTION-PLAYBOOK

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## When to extract

Extract a module to a separate deployable only when **all** are true:

1. Measurable scale or isolation need (GPU, credential blast radius, SLO split).
2. Schema already exclusive to that BC.
3. No cross-module JPA or shared tables.
4. Events/commands stable at v1+ with consumer contract tests.
5. Outbox/inbox proven under load in monolith.
6. On-call ownership named for the new service.

If any item fails → stay in monolith; fix modularity first.

## Extraction steps

1. **Freeze contracts** — event + HTTP ports in catalog; bump if needed.
2. **Move schema** — already separate; provision dedicated DB role.
3. **Split starter** — new Spring Boot app depending on module JAR.
4. **Dual-publish period** — monolith module disabled via feature flag; service consumes same queues.
5. **Cut traffic** — remove module from monolith classpath.
6. **Delete dead code** — only after soak; never leave two writers.

## Rollback

- Re-enable module in monolith; pause extracted service publishers.
- Inbox idempotency prevents duplicate side effects on replay.
- Delivery adapters remain gated by approval ids.

## Anti-patterns

- Extracting “because microservices” without metrics.
- Sharing a database user across services.
- Chatty sync HTTP for every workflow step (prefer events).
- Extracting half a schema.

## First recommended extractions

See `SERVICE-DECOMPOSITION-MAP.md`: Model Gateway, then Delivery.
