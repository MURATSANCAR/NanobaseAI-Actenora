# ADR-009: Schema-per-bounded-context

- **Status:** Accepted
- **Date:** 2026-07-25

## Context

Logical module boundaries fail when all tables share one undifferentiated schema with cross-FKs.

## Decision

Each bounded context receives its **own PostgreSQL schema** (and dedicated migration set). DB roles grant least privilege per schema. Cross-schema foreign keys for write coupling are forbidden.

## Consequences

- **Positive:** Physical enforcement of ownership; clearer migrations; easier extraction to separate databases later.
- **Negative:** No multi-schema joins for convenience — use APIs/events/read models.
- **Negative:** More migration pipelines to manage.
