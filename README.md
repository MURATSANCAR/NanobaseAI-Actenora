# NanobaseAI Actenora

Local-first, evidence-driven AI action and workflow platform.

Actenora plans and executes long-running enterprise workflows with multi-model local LLMs, human approval before any external side effect, and modular-monolith boundaries designed for later service extraction.

## Status — Phase 0

This repository is in **Phase 0 (architecture lock)**.

| Item | State |
|------|-------|
| Application source | Not present (greenfield) |
| Build / test suite | Not present |
| Architecture & ADR set | Locked under `docs/` |
| Business feature code | Intentionally absent |

See:

- [`docs/reviews/INITIAL-GAP-ANALYSIS.md`](docs/reviews/INITIAL-GAP-ANALYSIS.md) — baseline & gaps
- [`docs/product/PRODUCT-SCOPE.md`](docs/product/PRODUCT-SCOPE.md) — product scope
- [`docs/architecture/SYSTEM-CONTEXT.md`](docs/architecture/SYSTEM-CONTEXT.md) — system context
- [`docs/adr/`](docs/adr/) — architecture decision records
- [`docs/operations/LOCAL-DEVELOPMENT.md`](docs/operations/LOCAL-DEVELOPMENT.md) — local setup (target)
- [`artifacts/phase-0/baseline-capture.txt`](artifacts/phase-0/baseline-capture.txt) — raw command capture

## Target stack (locked)

| Concern | Choice |
|---------|--------|
| Shape | Modular monolith first (ADR-001) |
| Runtime | Java / Spring Boot (hexagonal modules) |
| Persistence | PostgreSQL, schema-per-bounded-context (ADR-009) |
| Messaging | RabbitMQ first (ADR-008) + transactional outbox/inbox (ADR-004) |
| Objects | Object-storage abstraction over MinIO-compatible API (ADR-007) |
| LLM | Local-only models; multi-model routing (ADR-005, ADR-006) |
| Delivery | Human approval before external side effects (ADR-010) |
| AI output | Evidence-first; no unsupported claims (ADR-011) |

## Non-goals for Phase 0

No application modules, migrations, APIs, or business workflows are implemented in this phase. See [`docs/product/NON-GOALS.md`](docs/product/NON-GOALS.md).
