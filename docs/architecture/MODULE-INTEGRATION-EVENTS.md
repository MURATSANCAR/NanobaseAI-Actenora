# MODULE-INTEGRATION-EVENTS

**Status:** Phase 3 (FAZ 3)  
**Date:** 2026-07-25

## Separation rule

| Kind | Package | Visibility | Transport |
|------|---------|------------|-----------|
| **Domain event** | `<module>.domain.event` | Module-internal | In-process only; never imported by other modules |
| **Integration event** | `<module>.api.event` | Public API (`api` named interface) | Outbox → broker (RabbitMQ) / Modulith application events |

Shared contracts:

- `com.nanobaseai.actenora.sharedkernel.domain.DomainEvent`
- `com.nanobaseai.actenora.sharedkernel.domain.IntegrationEvent`

## Publishing pattern

1. Aggregate mutates inside the owning module transaction.
2. Domain event recorded on the aggregate (optional, internal).
3. Application service maps to an **integration event** DTO in `api.event`.
4. Integration event is appended to the owning schema’s `outbox_messages` (ADR-004).
5. Outbox relay publishes to RabbitMQ; consumers use inbox idempotency in their schema.

## Consumer rules

- Depend on `other :: api` (integration event types / façades) only.
- Never import foreign `domain`, `application`, or `infrastructure` packages.
- Never inject foreign `*Repository` types.
- Store foreign IDs as opaque UUIDs / API ID records (`MeetingId`, `ApprovalId`, …).

## Forbidden

- Publishing JPA entities or domain aggregates on the bus
- Sharing a “common events” jar of internal domain types
- Cross-schema foreign keys for consistency (use choreography)
