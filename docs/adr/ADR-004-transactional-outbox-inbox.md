# ADR-004: Transactional outbox / inbox

- **Status:** Accepted
- **Date:** 2026-07-25

## Context

Publishing to RabbitMQ after a DB commit (or vice versa) risks dual-write failures: committed state without events, or events without state.

## Decision

Each owning schema implements **transactional outbox** (write state + outbox row in one transaction; publisher relays to RabbitMQ) and **inbox** (idempotent consumer dedupe by event id).

## Consequences

- **Positive:** At-least-once delivery with exactly-once *effects* via inbox keys.
- **Positive:** Foundation for future service extraction.
- **Negative:** Extra tables and relay process to operate.
- **Negative:** Latency of relay vs in-process bus — acceptable for domain events.
