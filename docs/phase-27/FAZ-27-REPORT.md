# FAZ-27-REPORT — Security, Retention & Compliance Hardening

**Date:** 2026-07-25  
**Status:** Implemented

## Summary

Production security and data lifecycle controls for Actenora: secure headers, CORS allowlist, rate limiting, upload/MIME limits, container hardening, supply-chain scans, network policies, AI/Graph egress controls, retention + legal hold, audit retention, signed URL expiry, and operational runbooks.

## Deliverables

| Requirement | Location |
|-------------|----------|
| Secure headers | `apps/platform-backend/.../security/SecurityHeadersFilter.java` |
| CORS allowlist | `.../security/CorsAllowlistConfig.java` + `actenora.security.cors` |
| Rate limiting | `.../security/RateLimitingFilter.java` |
| Upload size limits | Spring multipart + existing `VttUploadValidator` (25MB) |
| MIME / magic bytes | Existing `VttUploadValidator` (unchanged, covered by tests) |
| Container non-root | Existing Dockerfiles `USER actenora` |
| Read-only filesystem | Compose `read_only` + `infrastructure/k8s/security-context-readonly.yaml` |
| Secret scanning | `scripts/scan-secrets` + CI |
| Dependency vuln scan | `scripts/scan-dependencies` + CI |
| SBOM | Existing `scripts/generate-sbom` / CycloneDX |
| Network policy | `infrastructure/k8s/network-policies/*` |
| AI network egress deny | `apps/ai-orchestrator/.../egress.py` |
| Graph controlled egress | `GraphEgressPolicy` + `GraphHttpClient` |
| Retention job | `operations` `RetentionJobService` |
| Transcript deletion | `TranscriptIngestionService.deleteForRetention` |
| Private note deletion | `PrivateNoteRetentionService` |
| Legal hold prep | `LegalHold` + `operations.legal_holds` migration |
| Audit retention | `AuditRetentionPolicy` + `AuditRetentionService` |
| Signed URL expiry | TTL clamp + `AuthorizedUrl.isExpired` + in-memory enforcement |
| Certificate expiry alert | `docs/operations/CERTIFICATE-EXPIRY-ALERTS.md` |
| Backup/restore runbook | `docs/operations/BACKUP-RESTORE-RUNBOOK.md` |
| DR runbook | `docs/operations/DISASTER-RECOVERY-RUNBOOK.md` |

## Tests

| Case | Test |
|------|------|
| Expired URL | `TranscriptIngestionServiceTest.expiredAuthorizedUrlRejected` |
| Retention deletion | `RetentionAndLegalHoldTest.retentionDeletesExpiredTranscriptAndPrivateNote` + transcript/private-note service tests |
| Legal hold block | `RetentionAndLegalHoldTest.legalHoldBlocksRetentionDeletion` |
| Secret leak scan | `scripts/scan-secrets` (wired in CI / verify-faz27) |
| Unauthorized object access | `unauthorizedObjectKeyAccessDenied` / penetration cases |
| AI egress denial | `apps/ai-orchestrator/tests/test_egress.py` |
| Tenant isolation penetration | `tenantIsolationPenetrationDeniesCrossTenantObjectGet` |

## Commands

```bash
./scripts/scan-secrets
./scripts/scan-dependencies
./scripts/generate-sbom
./scripts/verify-faz27
make verify-faz27
```

## Risks / follow-ups

- In-process rate limit should be fronted by gateway/Redis in multi-replica prod.
- K8s NetworkPolicies are manifests-ready; cluster apply is environment-specific.
- Audit retention archives via eligibility listing — physical cold-store export adapters TBD.
- Graph egress IP allowlists in NetworkPolicy need environment-specific Microsoft IP / Private Link wiring.

## Next phase

FAZ 28 — load / resilience (per roadmap).
