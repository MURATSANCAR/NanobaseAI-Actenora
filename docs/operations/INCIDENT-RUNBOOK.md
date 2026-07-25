# INCIDENT-RUNBOOK

**Owner:** Platform on-call  
**Severity model:** SEV1 (data leak / wrong-tenant / unwanted external delivery) → SEV3 (degraded UX)

## First 5 minutes

1. Declare severity; open incident channel.  
2. Capture `ACTENORA_ENV`, release SHA, recent deploys.  
3. Check stop-the-line signals:
   - Cross-tenant access suspected  
   - Cloud LLM egress attempted  
   - Raw transcript seen in logs  
   - Duplicate external delivery  
   - Approval bypassed  
4. Prefer **mitigate then investigate** for SEV1/SEV2.

## Symptom playbooks

### A. Unwanted or duplicate delivery

1. Pause delivery worker / disable delivery enqueue feature flag.  
2. Confirm approval gate still required (`NoteApprovalGate`).  
3. Inspect delivery idempotency keys + attempt ledger for the `noteVersionId`.  
4. Dead-letter poison sends; do not replay until root cause known.  
5. Notify affected tenants; preserve audit entries (append-only).

### B. Suspected transcript / PII in logs

1. Freeze log shipping if possible; rotate access to log stores.  
2. Search for redaction failures (`PiiRedactor`, `SafeInferenceLog`).  
3. Patch logger; treat as SEV1 if raw transcript confirmed.  
4. Document retention / purge actions per DATA-CLASSIFICATION.

### C. Cloud LLM / data egress

1. Confirm orchestrator egress deny (`ACTENORA_AI_EGRESS_DENY_PUBLIC`).  
2. Block network path at egress firewall if needed.  
3. Quarantine any deployment URL outside allowlist.  
4. Audit recent inference jobs for destination hosts.

### D. Tenant isolation breach

1. Disable affected API routes / meeting collaboration endpoints.  
2. Capture `tenant_id` on requests vs resource ownership.  
3. Remember `FixedTenantContext` is **dev-only** — prod must bind Entra claims.  
4. Rotate credentials if tokens were confused across tenants.

### E. Queue meltdown / unbounded backlog

1. Enable / tighten `QueueDepthGuard` admission rejects.  
2. Scale consumers only if broker healthy.  
3. Divert poison to DLQ; never disable idempotency to “go faster”.

### F. Migration / Flyway failure

1. Stop rolling new instances.  
2. Do not `repair` blindly across colliding versions.  
3. Restore from backup if partial apply corrupted history.  
4. See [`ROLLBACK-RUNBOOK.md`](ROLLBACK-RUNBOOK.md) §Migrations.

## Comms

- Internal: severity, impact, next update ETA (30 min cadence for SEV1).  
- External: only after confirmed customer impact; no raw transcript excerpts in tickets.

## After-action

Within 5 business days: timeline, root cause, stop-condition mapping (FAZ 29 list), follow-up issues. Update this runbook if a new failure mode appeared.
