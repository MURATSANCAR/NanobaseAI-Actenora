# NON-GOALS — NanobaseAI Actenora

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## Explicitly out of scope

### Product

- Replacing NanobaseAI-QA or NanobaseAI-BI.
- General chatbot unrelated to meeting evidence.
- Fully autonomous external sends without human approval (production default).
- Training foundation models inside Actenora.
- Default use of hosted LLM APIs (OpenAI/Anthropic/Gemini) — ADR-005.
- Non-Microsoft meeting platforms in MVP (Zoom/Google Meet adapters deferred).

### Architecture

- Starting as a microservice mesh (ADR-001).
- Shared business tables across modules (ADR-002, ADR-009).
- Cross-module JPA entity reuse (ADR-012).
- Domain layer depending on Spring Web, JPA, RabbitMQ, MinIO, or Graph HTTP clients.
- Returning persistence entities as API responses.
- Field injection.

### Phase 0 documentation work

- Phase 0 does not require finishing product features.
- Phase 0 must not pretend broken builds are green.

## Anti-patterns to reject

1. Hard-coding vendor model ids (e.g. `QWEN27_FINAL`) in domain routing enums — use catalog/role ids resolved by `model-management`.
2. Writing another module’s schema.
3. Publishing events without outbox.
4. Delivering from draft/unapproved insight state.
5. AI narrative without evidence bindings.
