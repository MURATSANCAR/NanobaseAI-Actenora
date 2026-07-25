# ADR-001: Modular monolith first

- **Status:** Accepted
- **Date:** 2026-07-25
- **Deciders:** Actenora architecture lock (Phase 0)

## Context

Actenora needs clear bounded contexts, eventual service extraction, and fast iteration. Starting with a microservice mesh would add network latency, ops burden, and premature boundaries while the product domain is still being proven.

## Decision

Build Actenora as a **modular monolith**: one deployable (API + worker profiles), multiple modules with enforced hexagonal boundaries. Extract services only per `SERVICE-EXTRACTION-PLAYBOOK.md`.

## Consequences

- **Positive:** Shared transactions within a schema; simpler local dev; cheaper refactor of boundaries.
- **Positive:** Extraction remains possible if schemas/events stay clean.
- **Negative:** Requires discipline (ArchUnit) so modules do not rot into a ball of mud.
- **Negative:** Single blast radius until extraction — mitigated by module isolation and approval gates.
