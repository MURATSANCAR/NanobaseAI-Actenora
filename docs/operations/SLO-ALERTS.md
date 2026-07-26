# SLO-ALERTS

**Owner:** Platform SRE  
**Related:** [`SLO-ALERTS.md`](SLO-ALERTS.md) consumers — Grafana / Datadog / Prometheus Alertmanager

## Service level indicators (SLIs)

| SLI | Definition | Target (30d) | Source |
|-----|------------|--------------|--------|
| **Availability** | Ratio of successful synthetic + ingress probes | **99.9%** (43m/month error budget) | `/api/health`, `/actuator/health/readiness` |
| **Latency p95** | HTTP server request duration p95 for `/api/**` | **≤ 800 ms** | Micrometer `http.server.requests` |
| **Outbox lag** | `now() - max(published_at)` for unpublished outbox rows | **≤ 60 s** steady state | `operations.outbox_events` / relay metrics |
| **DLQ rate** | DLQ enqueue rate / total consumed messages | **≤ 0.1%** over 1h | RabbitMQ `actenora.dlq` + consumer metrics |

## SLO error budgets

| SLO | Budget (30d) | Fast burn (page) | Slow burn (ticket) |
|-----|--------------|------------------|---------------------|
| Availability 99.9% | 43.2 min downtime | 2% budget in 1h | 5% budget in 6h |
| Latency p95 ≤ 800ms | 5% of requests may exceed | p95 > 1.2s for 15m | p95 > 900ms for 1h |
| Outbox lag ≤ 60s | 0.1% of minutes | lag > 300s for 5m | lag > 120s for 30m |
| DLQ rate ≤ 0.1% | 0.1% messages | rate > 1% for 15m | rate > 0.3% for 1h |

## Burn-rate alert sketches (Prometheus-style)

### Availability — multi-window

```yaml
# Page: fast burn — 14.4x budget consumption over 1h AND 5m window elevated
- alert: ActenoraAvailabilityFastBurn
  expr: |
    (
      sum(rate(actenora_probe_success_total[5m])) /
      sum(rate(actenora_probe_attempts_total[5m]))
    ) < 0.985
    and
    (
      1 - (
        sum(increase(actenora_probe_success_total[1h])) /
        sum(increase(actenora_probe_attempts_total[1h]))
      )
    ) > 14.4 * 0.001
  for: 2m
  labels:
    severity: page
  annotations:
    summary: "Actenora availability SLO fast burn"
    runbook: "docs/operations/INCIDENT-RUNBOOK.md"

# Ticket: slow burn — 6h window
- alert: ActenoraAvailabilitySlowBurn
  expr: |
    (
      1 - (
        sum(increase(actenora_probe_success_total[6h])) /
        sum(increase(actenora_probe_attempts_total[6h]))
      )
    ) > 6 * 0.001
  for: 15m
  labels:
    severity: ticket
```

### Latency p95

```yaml
- alert: ActenoraLatencyP95High
  expr: |
    histogram_quantile(0.95,
      sum(rate(http_server_requests_seconds_bucket{uri=~"/api/.*"}[5m])) by (le)
    ) > 0.8
  for: 15m
  labels:
    severity: ticket
  annotations:
    summary: "API p95 latency above 800ms SLO"
```

### Outbox lag

```yaml
- alert: ActenoraOutboxLagHigh
  expr: actenora_outbox_lag_seconds > 60
  for: 5m
  labels:
    severity: ticket
  annotations:
    summary: "Outbox relay lag exceeds 60s"
    runbook: "docs/operations/ROLLBACK-RUNBOOK.md#application--image-rollback"

- alert: ActenoraOutboxLagCritical
  expr: actenora_outbox_lag_seconds > 300
  for: 2m
  labels:
    severity: page
```

### DLQ rate

```yaml
- alert: ActenoraDlqRateElevated
  expr: |
    sum(rate(actenora_rabbitmq_dlq_messages_total[15m])) /
    sum(rate(actenora_rabbitmq_consumed_messages_total[15m])) > 0.01
  for: 5m
  labels:
    severity: page
  annotations:
    summary: "DLQ rate above 1% — investigate poison messages"
```

## Dashboards (minimum panels)

1. Availability SLI + remaining error budget
2. p50/p95/p99 latency by route
3. Outbox depth + lag time series
4. DLQ depth + enqueue rate
5. HPA replica count vs CPU (platform-backend)

## Escalation

| Severity | Response | Channel |
|----------|----------|---------|
| page | ≤ 15 min ack | Pager / on-call |
| ticket | ≤ 4h ack | Team queue |

Cross-link: [`INCIDENT-RUNBOOK.md`](INCIDENT-RUNBOOK.md), [`DISASTER-RECOVERY-RUNBOOK.md`](DISASTER-RECOVERY-RUNBOOK.md).
