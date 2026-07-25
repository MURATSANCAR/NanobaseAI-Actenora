# SECURITY-BASELINE

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## 1. Control pillars

| Pillar | Baseline |
|--------|----------|
| AuthN | Entra ID OIDC (JWT resource server) in production; local mock IdP headers only on non-prod (`actenora.security.auth.mode`) |
| AuthZ | Tenant-scoped RBAC via Identity roles → permission catalog; never trust body/query tenant IDs |
| Delivery | Human approval before external side effects (ADR-010) |
| LLM | Local-only default (ADR-005) |
| Secrets | Env/file/Vault refs — never commit secrets |
| Data | Classification labels + least privilege (see DATA-CLASSIFICATION) |
| Audit | Append-only audit for security-relevant actions |
| Supply chain | Lockfiles / dependency scanning in Phase 1+ CI |

Suspended tenants are **full-blocked** — see [SUSPENDED-TENANT-POLICY.md](./SUSPENDED-TENANT-POLICY.md).

## 2. Secret & config methods (target)

| Method | Use |
|--------|-----|
| Environment variables | Non-secret config + secret refs in containers |
| `.env` local files | Dev only; gitignored |
| Docker/K8s secrets | Deployed runtimes |
| Optional Vault | Production; path refs like sibling BI product |
| Spring Config | Hierarchical `application-*.yml` without secrets inline |

**Phase 0 reality:** no runtime config files yet beyond documentation.

## 3. Network

- Deny egress to public LLM APIs in default profile.
- TLS for external client traffic; private networks for DB/queue/store/LLM.
- Delivery adapters allowlist destinations per workspace.

## 4. Threats & mitigations (initial)

| Threat | Mitigation |
|--------|------------|
| Prompt injection → rogue delivery | Approval gate; schema validation; evidence-only claims |
| Credential theft from delivery module | Isolate secrets; future extract delivery service |
| Cross-tenant data leak | Tenant/workspace checks on every query |
| Model data exfiltration | Local runtime; no training upload |
| Replay attacks on commands | Idempotency keys + inbox |

## 5. Secure coding defaults

- Constructor injection; no field injection.
- Parameterized persistence; no string-concat SQL.
- DTO records at API boundary.
- Principle of least module privilege on DB roles (per schema).
