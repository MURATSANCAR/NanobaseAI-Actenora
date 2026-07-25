# SERVICE-EXTRACTION-RUNBOOK

**Owner:** Platform on-call  
**Related:** [`SERVICE-EXTRACTION-PLAYBOOK.md`](../architecture/SERVICE-EXTRACTION-PLAYBOOK.md), [`MICROSERVICE-EXTRACTION-READINESS.md`](../reviews/MICROSERVICE-EXTRACTION-READINESS.md)

## When to use

Cutting a bounded-context worker out of `apps/platform-backend` into `services/*` after playbook gates pass.

## Current extraction inventory

| Service | State |
|---------|-------|
| `teams-integration-service` | Shell started |
| `transcript-worker` | Shell started |
| `model-worker` | Reserved |
| `document-renderer` | Reserved |
| `delivery-worker` | Reserved (domain worker loop exists in module) |

**Do not extract** while `./scripts/test-all` is red or Flyway versions collide.

## Standard cutover (dual-publish)

1. **Freeze contracts** — bump event/OpenAPI if needed; consumer contract tests green.  
2. **Provision** — dedicated DB role for BC schema; Rabbit topology; object-storage prefix.  
3. **Deploy service** — consume same queues; publishers still in monolith **or** dual-publish via platform extraction adapters (`@ConditionalOnProperty`).  
4. **Shadow / soak** — compare side effects via inbox idempotency; watch DLQ.  
5. **Flip flag** — disable monolith module / enable remote client.  
6. **Remove dead path** — only after soak window; never leave two writers.

### Transcript example flag

Platform stubs under `com.nanobaseai.actenora.platform.extraction.transcript` are gated by property. Prefer property names documented in `application-*.yml` for the release; default **off** in prod until soak completes.

## Validation checklist

- [ ] Health + readiness of extracted service  
- [ ] No cross-schema writes  
- [ ] Outbox rows drain; inbox dedupes redelivery  
- [ ] Tenant isolation tests still green  
- [ ] SBOM regenerated for new image  

## Rollback

1. Re-enable monolith module classpath / feature flag.  
2. Pause extracted service publishers.  
3. Leave consumers draining; inbox prevents duplicate side effects.  
4. Delivery remains gated by approval ids.  

Detail: [`ROLLBACK-RUNBOOK.md`](ROLLBACK-RUNBOOK.md) §Service extraction.

## Anti-patterns

- Extracting for fashion without metrics  
- Shared DB user across services  
- Sync chatty HTTP for every workflow step  
- Half-schema splits  
- Shipping empty Docker placeholders without contracts
