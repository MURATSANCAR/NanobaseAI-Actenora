# SCALING-STRATEGY

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## 1. Scale dimensions

| Dimension | First lever | Later lever |
|-----------|-------------|-------------|
| HTTP QPS | Replicas of `actenora-api` | Edge cache for read models |
| Queue lag | Replicas of `actenora-worker` by queue | Split queues per BC |
| DB | Vertical + indexes; schema isolation | Read replicas per hot schema |
| LLM | Dedicated GPU host for runtime | Extract Model Gateway + pool |
| Object store | MinIO erasure / capacity | External S3-compatible |
| Delivery | Rate-limit per adapter | Extract Delivery service |

## 2. Scaling order

1. Modular monolith with separate API/worker processes.
2. Horizontal workers on RabbitMQ depth.
3. Isolate LLM runtime hardware.
4. Extract Model Gateway / Delivery per playbook.
5. Only then consider splitting Workflow.

## 3. SLOs (intent)

| Path | Intent |
|------|--------|
| Approval API latency | Interactive (low hundreds of ms) |
| Plan generation | Async; minutes acceptable |
| Delivery attempt | Adapter-dependent; durable retries |

## 4. Backpressure

- Reject or delay new `PlanRequested` when inference queue > threshold.
- Never bypass approval to “catch up”.
- DLQ for poison; alert on depth.

## 5. Multi-tenant scale

Workspace isolation in data access; noisy-neighbor controls via per-workspace quotas in modelgw and delivery.
