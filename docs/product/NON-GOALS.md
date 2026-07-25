# NON-GOALS — NanobaseAI Actenora

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## Explicitly out of scope

### Product

- Replacing NanobaseAI-QA (test generation / IR pipelines).
- Replacing NanobaseAI-BI (NL2SQL / reporting gateway).
- General-purpose chatbot without evidence binding.
- Fully autonomous external actions without human approval (production default).
- Training / fine-tuning foundation models inside Actenora (consume, do not train).
- Multi-cloud LLM failover to OpenAI / Anthropic / Gemini in default policy (see ADR-005).

### Architecture / delivery

- Starting as a microservice mesh (ADR-001: modular monolith first).
- Shared database tables across bounded contexts (ADR-002, ADR-009).
- Cross-module JPA entity reuse (ADR-012).
- Domain layer depending on Spring Web, JPA, RabbitMQ, MinIO, or HTTP clients.
- Returning JPA entities as API responses.
- Field injection in Spring components.

### Phase 0 specifically

- Application / business feature code.
- Database migrations of production schemas.
- Runnable API, workers, or UI.
- Production deployment manifests beyond documentation intent.
- Performance tuning or load harness implementation.

## Deferred (may become goals later)

| Item | Earliest consideration |
|------|------------------------|
| Extracted services for Workflow / Model Gateway | After modular monolith proves load & ownership |
| Optional cloud LLM break-glass profile | Security & legal review; never default |
| Marketplace of third-party action adapters | After core approval + delivery contracts stabilize |
| Real-time collaborative editing UI | After workflow/approval MVP |

## Anti-patterns we will reject in review

1. Hard-coding a single model id (e.g. Qwen) in domain or use-case code.
2. Writing to another context’s schema “just this once”.
3. Publishing domain events without outbox.
4. Delivering externally from a draft/proposed state.
5. AI narrative without evidence IDs when claims are factual.
