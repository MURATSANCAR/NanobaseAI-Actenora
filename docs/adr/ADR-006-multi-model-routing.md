# ADR-006: Multi-model routing

- **Status:** Accepted
- **Date:** 2026-07-25

## Context

Hard-coding a single model (e.g. Qwen) into domain or use-case code freezes quality, prevents task-appropriate sizing, and couples tests to one vendor string.

## Decision

All inference goes through **Model Gateway** with a **capability matrix** and **routing policies**. Callers specify `taskType`, not vendor model ids. Catalog ids (e.g. `local.reasoner.default`) map to runtime models operationally.

## Consequences

- **Positive:** Swap/upgrade models without rewriting business logic.
- **Positive:** Right-size models per task (fast classifier vs deep reasoner).
- **Negative:** More moving parts (catalog, health, policies).
- **Negative:** Requires evaluation harness before promoting models.
