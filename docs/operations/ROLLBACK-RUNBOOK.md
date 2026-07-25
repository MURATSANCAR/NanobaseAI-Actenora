# ROLLBACK-RUNBOOK

**Owner:** Platform on-call  
**Related:** [`INCIDENT-RUNBOOK.md`](INCIDENT-RUNBOOK.md), [`SERVICE-EXTRACTION-RUNBOOK.md`](SERVICE-EXTRACTION-RUNBOOK.md)

## Principles

1. Prefer feature-flag rollback over schema downgrade.  
2. Inbox idempotency makes at-least-once replay safe; **do not** disable it during rollback.  
3. Never “fix” delivery by skipping approval.  
4. Append-only audit is not rewritten.

## Application / image rollback

1. Identify last known-good image digests (platform-backend, ai-orchestrator, workers, portal).  
2. Redeploy previous digests; keep config compatible (no forward-only required env without defaults).  
3. Verify health endpoints and a single dry-run path (non-prod tenant if available).  
4. Watch outbox lag, DLQ depth, delivery attempts.

## Feature flags / extraction

| Situation | Action |
|-----------|--------|
| Extracted service misbehaving | Re-enable monolith module; pause extracted publishers |
| Dual-publish mismatch | Disable remote client property; drain remote consumers |
| Bad routing policy | Revert tenant allowlist / role preference; record provenance |

## Model pool rollback

1. Point role preference to previous deployment id.  
2. Drain bad deployment.  
3. Confirm no mock/cloud provider became active.  
4. Keep decision provenance for the rollback event.

## Delivery rollback

1. Stop worker loop.  
2. Leave `PROVIDER_ACCEPTED` items unconfirmed until investigation.  
3. Do not re-send without new idempotency scope or explicit operator replay from DLQ tools.  
4. If MailHog/fake provider was somehow active in prod: treat as SEV1 config incident; switch to Graph provider only after approval gate verification.

## Migrations

**Prefer roll forward** with a new Flyway version.

If a release must be aborted mid-migrate:

1. Stop app instances using the new code.  
2. Restore DB from pre-migrate snapshot if history is inconsistent (especially with **duplicate version ids** — known FAZ 29 risk).  
3. Do not delete `flyway_schema_history` rows casually.  
4. Re-test fresh DB migrate on a clone before retrying prod.

## Data / object storage

- Raw transcripts remain in tenant-prefixed object keys; rollback of app code does not delete objects.  
- If a bad parser wrote corrupt normalized rows, mark transcripts for reparse rather than silent overwrite of audit.

## Verification after rollback

- [ ] `./scripts/test-all` green on the rolled-back tag (CI)  
- [ ] No cloud LLM egress  
- [ ] No raw transcript in sample logs  
- [ ] Approval still required before delivery  
- [ ] Tenant isolation smoke (two tenants, cross-read denied)

## When rollback is insufficient

Escalate to incident SEV1 and isolate network (Graph send, LLM egress, portal writes) until a forward fix is ready.
