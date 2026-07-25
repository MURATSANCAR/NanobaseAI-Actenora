# ADR-003: Event-driven long-running workflows

- **Status:** Accepted
- **Date:** 2026-07-25

## Context

Actenora workflows span intake → planning → approval → delivery and can run minutes to days. Synchronous orchestration does not survive restarts, timeouts, or partial failures.

## Decision

Use **event-driven, durable workflows** owned by the Workflow context, with explicit state machines and choreography via domain events/commands. No distributed XA across contexts.

See `DISTRIBUTED-WORKFLOWS.md` and `STATE-MACHINES.md`.

## Consequences

- **Positive:** Restart-safe; scalable workers; clear audit of transitions.
- **Negative:** Eventual consistency; requires idempotent handlers.
- **Negative:** Harder mental model than request/response — mitigated by catalogs and diagrams.
