# ADR-008: RabbitMQ-first broker

- **Status:** Accepted
- **Date:** 2026-07-25

## Context

Actenora needs durable messaging for commands, domain events, retries, and DLQs. Alternatives (Kafka, Redis streams, cloud buses) optimize for different scales; the team needs one primary for the monolith phase.

## Decision

Use **RabbitMQ** as the first-class broker for commands and domain events. Kafka (or others) may be reconsidered only if proven throughput/retention needs exceed RabbitMQ after measurement — not by default.

## Consequences

- **Positive:** Mature routing, DLQ, delayed/retry patterns; good fit for workflow choreography.
- **Positive:** Simpler ops for early stages than Kafka clusters.
- **Negative:** Not ideal as a multi-day analytics log store — use audit/DB for that.
- **Negative:** Migration cost if Kafka is later required — keep event payloads broker-agnostic.
