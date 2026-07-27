# ADR-010: Human approval before external delivery

- **Status:** Accepted
- **Date:** 2026-07-25

## Context

AI-proposed actions can email customers, open tickets, or call ERP APIs. Autonomous side effects are an unacceptable default risk for confidential enterprise workflows.

## Decision

**No external side effect** may execute unless the Approval context has recorded `ApprovalGranted` for the specific plan version. Delivery orders must bind `approvalId`. Bypass flags are forbidden in production profiles.

### Clarification — organizer draft vs external final

`DeliveryIntent.DRAFT_ORGANIZER` is an **internal** notification to the meeting organizer that a draft minutes note is ready for review. It does **not** constitute external distribution and therefore does **not** require `ApprovalGranted`. Draft organizer mail is enqueued **only** via Delivery (`DeliveryApi.enqueueDraftOrganizerNotification`); a separate JavaMail / Spring `JavaMailSender` bypass path is forbidden.

`DeliveryIntent.FINAL_EXTERNAL` remains gated: it may only enqueue after `ApprovalGranted` for the approved note version. No production bypass exists for this path.

## Consequences

- **Positive:** Strong governance; auditor-friendly; reduces prompt-injection blast radius.
- **Negative:** Higher human latency; needs good UX and notification SLAs.
- **Negative:** Temptation to add “auto-approve” — only via explicit future ADR with risk acceptance.
