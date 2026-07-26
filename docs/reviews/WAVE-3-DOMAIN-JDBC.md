# Wave 3 — Domain JDBC adapters

## Scope

PostgreSQL JDBC persistence for ten bounded-context domain ports, gated by `actenora.persistence.mode=jdbc`. Follows Wave 2 pattern: `JdbcTemplate`, `JdbcJson`/`JdbcInstant`, optimistic locking where aggregates expose `version`, append-only for ledger/audit-style stores.

## Adapters created

| Module | Adapter | Port | Flyway tables |
|--------|---------|------|---------------|
| **audit** | `JdbcAuditEntryStore` | `AuditEntryStore` | `audit.entries` (V221); immutability triggers V224 |
| **operations** | `JdbcLegalHoldRepository` | `LegalHoldRepository` | `operations.legal_holds` (V233) |
| **model-management** | `JdbcModelDefinitionRepository` | `ModelDefinitionRepository` | `modelmanagement.model_definition`, `model_capability` (V162) |
| | `JdbcModelDeploymentRepository` | `ModelDeploymentRepository` | `modelmanagement.model_deployment` (V162) |
| **policy** | `JdbcTenantPolicyRepository` | `TenantPolicyRepositoryPort` | `policy.tenant_policy_overrides`, `tenant_policy_materialized` (V121) |
| | `JdbcQuotaUsageStore` | `QuotaUsagePort` | `policy.quota_usage_daily`, `concurrency_usage` (V121) |
| | `PolicyJsonCodec` | (internal) | JSONB columns V121 |
| **meeting** | `JdbcMeetingOccurrenceRepository` | `MeetingOccurrenceRepository` | `meeting.meeting_occurrences` (V140_1) |
| | `JdbcMeetingSeriesRepository` | `MeetingSeriesRepository` | `meeting.meeting_series` (V140_1) |
| | `JdbcMeetingParticipantRepository` | `MeetingParticipantRepository` | `meeting.meeting_participants` (V140_1) |
| **transcript** | `JdbcTranscriptRepository` | `TranscriptRepository` | `transcript.transcripts` (V152) |
| | `JdbcTranscriptSegmentRepository` | `TranscriptSegmentRepository` | `transcript.transcript_segments` (V152) |
| **ai-processing** | `JdbcAiJobRepository` | `AiJobRepository` | `aiprocessing.ai_jobs` (V173) |
| | `JdbcAiAttemptRepository` | `AiAttemptRepository` | `aiprocessing.ai_attempts` (V173) |
| **meeting-intelligence** | `JdbcMeetingNoteRepository` | `MeetingNoteRepository` | `meetingintelligence.meeting_notes` (V181) |
| | `JdbcMeetingNoteVersionRepository` | `MeetingNoteVersionRepository` | `meetingintelligence.meeting_note_versions` (V181 columns; V182 `approval_status`/`provenance_json` gap — see deferred) |
| | `JdbcLedgerEventStore` | `LedgerEventStore` | `meetingintelligence.ledger_events` (V184) |
| | `JdbcLedgerProjectionRepository` | `LedgerProjectionRepository` | `meetingintelligence.meeting_briefs` (V184) |
| | `LedgerProjectionJsonCodec` | (internal) | `payload_json` snapshot |
| **approval** | `JdbcApprovalRequestRepository` | `ApprovalRequestRepository` | 4× `approval.*` (V191) |
| | `JdbcParticipantDisputeRepository` | `ParticipantDisputeRepository` | `approval.participant_disputes` (V191) |
| **template** | `JdbcMeetingTemplateRepository` | `MeetingTemplateRepository` | `template.meeting_template`, `template_version` (V202) |
| **delivery** | `JdbcDeliveryOrderRepository` | `DeliveryOrderRepository` | `delivery.orders` (V212) |
| | `JdbcDeliveryRequestRepository` | `DeliveryRequestRepository` | `delivery.requests`, `attempts`, `provider_messages`, `dead_letters` (V213) |

## Configuration classes

Each module exposes `*JdbcPersistenceConfiguration` with `@ConditionalOnProperty(name = "actenora.persistence.mode", havingValue = "jdbc")`. InMemory `@ConditionalOnMissingBean` adapters remain default for local/test.

- `AuditJdbcPersistenceConfiguration`
- `OperationsJdbcPersistenceConfiguration`
- `ModelManagementJdbcPersistenceConfiguration`
- `PolicyJdbcPersistenceConfiguration`
- `MeetingJdbcPersistenceConfiguration`
- `TranscriptJdbcPersistenceConfiguration`
- `AiProcessingJdbcPersistenceConfiguration`
- `MeetingIntelligenceJdbcPersistenceConfiguration`
- `ApprovalJdbcPersistenceConfiguration`
- `TemplateJdbcPersistenceConfiguration`
- `DeliveryJdbcPersistenceConfiguration`

## Audit immutability (V224)

`V224__audit_entries_immutability_triggers.sql` adds PostgreSQL triggers on `audit.entries` that reject UPDATE and DELETE at the database layer (append-only enforcement complementing application INSERT-only adapters).

## Domain rehydrate additions

- `AuditEntry.rehydrate` (read path for timeline queries)
- `LegalHold.rehydrate`
- `ModelDefinition.rehydrate` (with capabilities)
- `DeliveryRequest.rehydrate`, `DeliveryAttempt.rehydrate`, `DeliveryOrder.rehydrate`

## Deferred ports (still InMemory)

| Module | Port / store | Reason |
|--------|--------------|--------|
| **meeting** | `BusinessContextRepository` | Out of Wave 3 core V140_1 scope |
| | Collaboration ports (agenda, markers, notes, tasks) | V143 tables — Wave 4+ |
| | Relation / continuity suggestion repos | V141 — Wave 4+ |
| **transcript** | `TenantDictionaryRepository`, `NormalizationRunRepository`, `KnownMeetingOccurrenceStore` | Later transcript migrations |
| **ai-processing** | Routing stores, prompt registry, pipeline runs | V172/V174 operational tables |
| **meeting-intelligence** | Decision/ActionItem/Risk/Evidence/Quality repos | V181 child entities — Wave 4+ |
| | `approval_status` on note versions (V182) | JDBC adapter maps V181 provenance columns only |
| | Validation run / manual review repos | V183 |
| **template** | `RenderJobRepository`, `RenderedDocumentRepository`, `NoteTemplateLockRepository` | V202 render pipeline |
| **policy** | `model_allowlist` table | Not yet bound to a port adapter |
| **operations** | Retention job support stores | Messaging / ops center (V232) |
| **microsoft-connection** | Subscription / calendar sync | Separate wave |
| **audit** | (done Wave 2) | — |

## Compile

```bash
./mvnw -pl apps/platform-backend -am -DskipTests compile
```

## Exit criteria

- [x] JDBC adapters implement listed ports against Flyway schemas
- [x] `spring-boot-starter-jdbc` optional dependency on each module
- [x] Prod profile can set `actenora.persistence.mode=jdbc` (from Wave 2)
- [ ] Module-level JDBC integration tests (follow-up)

## Follow-ups

- Wave 4: meeting collaboration/relation JDBC, MI child-entity repos, template render jobs
- Testcontainers Postgres tests per adapter (mirror `JdbcUserRepositoryTest` pattern)
- `noteToOccurrence` map persistence in ledger projection codec when domain exposes it
