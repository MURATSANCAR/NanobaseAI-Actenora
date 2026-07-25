# ADR-010: Human approval before external delivery

- **Status:** Accepted
- **Date:** 2026-07-25

## Context

AI-proposed actions can email customers, open tickets, or call ERP APIs. Autonomous side effects are an unacceptable default risk for confidential enterprise workflows.

## Decision

**No external side effect** may execute unless the Approval context has recorded `ApprovalGranted` for the specific plan version. Delivery orders must bind `approvalId`. Bypass flags are forbidden in production profiles.

## Consequences

- **Positive:** Strong governance; auditor-friendly; reduces prompt-injection blast radius.
- **Negative:** Higher human latency; needs good UX and notification SLAs.
- **Negative:** Temptation to add “auto-approve” — only via explicit future ADR with risk acceptance.
