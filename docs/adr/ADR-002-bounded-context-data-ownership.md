# ADR-002: Bounded context data ownership

- **Status:** Accepted
- **Date:** 2026-07-25

## Context

Shared databases and “common entity” jars create hidden coupling that blocks extraction and corrupts invariants.

## Decision

Each bounded context **owns its data**. Other contexts may store opaque foreign ids and integrate via application ports or events — never by writing another context’s tables.

Ownership catalog: `docs/architecture/DATA-OWNERSHIP.md`.

## Consequences

- **Positive:** Clear invariants; extractable schemas; safer evolution.
- **Negative:** Possible data duplication of references; eventual consistency across contexts.
- **Negative:** Developers must resist convenient cross-schema joins for writes.
