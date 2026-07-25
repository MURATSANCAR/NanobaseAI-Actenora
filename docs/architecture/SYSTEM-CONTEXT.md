# SYSTEM-CONTEXT

**Status:** Locked for Phase 0  
**Date:** 2026-07-25

## 1. System

**Actenora** — NanobaseAI Teams meeting intelligence platform (modular monolith + AI orchestrator).

## 2. Context diagram

```text
┌──────────────────┐     ┌──────────────────┐
│ Operators /      │     │ Microsoft 365    │
│ Approvers        │     │ (Graph / Teams)  │
└────────┬─────────┘     └────────┬─────────┘
         │ HTTPS                  │ Graph API
         ▼                        ▼
┌─────────────────────────────────────────────┐
│                 ACTENORA                    │
│  web-portal · teams-meeting-app             │
│  platform-backend (modular monolith)        │
│  ai-orchestrator (Python)                   │
└───────┬──────────┬──────────┬───────────────┘
        ▼          ▼          ▼
   PostgreSQL   RabbitMQ   MinIO
        │
        ▼
   Local LLM runtime (Ollama/vLLM/…)
```

## 3. Actors

| Actor | Intent |
|-------|--------|
| Operator / approver | Review insights; approve delivery |
| Tenant admin | Connect Microsoft tenant; set policies |
| Microsoft Graph | Source of meetings, transcripts, subscriptions |
| External mail / channels | Recipients of **approved** deliveries only |
| Auditor | Read audit + evidence trail |

## 4. Trust boundaries

| Boundary | Rule |
|----------|------|
| UI → platform-backend | Authenticated, tenant-scoped |
| platform-backend → Graph | Per-tenant credentials in microsoft-connection |
| Actenora → Local LLM | Private network; default deny cloud LLM |
| Actenora → SMTP/external | Only after ApprovalGranted |
| Schema boundaries | No cross-module writes |

## 5. Sibling products

QA and BI are separate repos; Actenora does not embed them.
