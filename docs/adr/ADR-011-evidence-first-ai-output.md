# ADR-011: Evidence-first AI output

- **Status:** Accepted
- **Date:** 2026-07-25

## Context

Ungrounded LLM narratives invent facts, evidence IDs, and metrics. Sibling NanobaseAI products already reject source-free claims.

## Decision

AI outputs that assert facts **must** reference supplied `evidenceIds` (or be marked non-factual summary). Producers validate: referenced ids ⊆ input set; schema validation fails closed; branding remains NanobaseAI (no vendor model names in user text).

## Consequences

- **Positive:** Higher trust; testable golden fixtures; safer approvals.
- **Negative:** More engineering on validators and prompt schemas.
- **Negative:** Some creative copy restricted — acceptable for enterprise actions.
