# ADR-012: No cross-module JPA entities

- **Status:** Accepted
- **Date:** 2026-07-25

## Context

Sharing JPA `@Entity` classes across modules recreates a distributed monolith’s worst coupling inside a single repo and blocks schema ownership.

## Decision

JPA entities are **private to their module’s infrastructure**. Other modules integrate via DTOs/records, domain ids, and ports. No `*-entities` shared library for business tables. Shared-kernel may hold only primitive value types / ids, not entities.

## Consequences

- **Positive:** Enforces ADR-002/009; ArchUnit can police imports.
- **Negative:** Occasional mapping boilerplate.
- **Negative:** Cannot use JPA associations across contexts — by design.
