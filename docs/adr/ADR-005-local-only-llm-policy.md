# ADR-005: Local-only LLM policy

- **Status:** Accepted
- **Date:** 2026-07-25

## Context

Actenora handles confidential evidence and action plans. Sending prompts to hosted LLM APIs creates data residency, exfiltration, and vendor lock risks. Sibling NanobaseAI products already emphasize local inference.

## Decision

**Default profiles allow only local LLM runtimes** (Ollama, vLLM, llama.cpp server, or equivalent on private network). Outbound calls to OpenAI/Anthropic/Gemini/etc. are denied unless a non-default break-glass profile is explicitly approved by security.

## Consequences

- **Positive:** Stronger confidentiality baseline; predictable cost/latency on owned hardware.
- **Negative:** Ops must provision GPU/CPU capacity and model updates.
- **Negative:** Quality ceiling depends on local model choice — mitigated by multi-model routing (ADR-006).
