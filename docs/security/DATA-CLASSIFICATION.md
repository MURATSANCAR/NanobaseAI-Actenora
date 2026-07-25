# DATA-CLASSIFICATION

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## Levels

| Level | Name | Examples | Handling |
|-------|------|----------|----------|
| L0 | Public | Product docs, non-sensitive enums | No special control |
| L1 | Internal | Workflow definitions, non-PII configs | Authn required |
| L2 | Confidential | Evidence content, plans, case data | Workspace ACL; encrypt in transit; restrict logs |
| L3 | Restricted | Secrets, API keys, delivery credentials, raw tokens | Secret store only; never LLM logs |
| L4 | Regulated (if applicable) | Future: health/financial special categories | Policy pack + retention |

## By bounded context (default)

| Context | Default level |
|---------|---------------|
| identity | L2–L3 |
| evidence / artifact | L2 |
| planning / knowledge / case | L2 |
| modelgw prompts/payloads | L2 (prompts may embed L2) |
| approval decisions | L2 |
| delivery credentials | L3 |
| audit | L2 (may reference L2 ids) |
| notify templates | L1–L2 |

## LLM rules

- Do not send L3 secrets to models.
- Prefer evidence **ids + redacted excerpts** over full blobs when possible.
- Inference logs store hashes/ids; raw prompt retention is opt-in and time-bounded.

## Retention (intent)

| Data | Intent |
|------|--------|
| audit.entries | Long-lived |
| inference payloads | Short TTL unless investigation flag |
| object store evidence | Workspace retention policy |
| outbox/inbox | Until published/processed + grace |

Exact TTLs configured in Phase 1+ ops, not code in Phase 0.
