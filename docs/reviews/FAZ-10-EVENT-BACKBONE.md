# FAZ-10 Event Backbone / Outbox / Inbox / DLQ

**Phase:** FAZ 10  
**Date:** 2026-07-25  
**Status:** Complete (InMemory runtime wired; Rabbit/JDBC deferred)

## 1. Faz özeti

Shared-kernel messaging runtime (outbox/inbox/DLQ/retry/replay) ve 14 şema DDL zaten vardı. Bu turda: Flyway sürüm çakışmaları giderildi, platform’da paylaşılan InMemory `EventBackbone` + relay start, meeting → outbox publisher, meeting→transcript occurrence choreography (inbox), Rabbit naming `definitions.json` ile hizalandı.

## 2. Bu turda değişenler

- Flyway: approval `V192`, meeting-intelligence `V185`, audit `V223` (transcript `V153` önceki turda)
- `FanOutEventTransport` — local fan-out + recording
- `OutboxMeetingEventPublisher` + `MeetingOccurrenceUpserted.v1`
- `EventBackbonePlatformConfiguration` — shared stores, relay start, ops Primary beans
- `RabbitDlxTopology` → `actenora.domain` / `actenora.dlx` / `actenora.dlq`
- Docs: `MODULE-INTEGRATION-EVENTS.md` (`outbox_event`), `EVENT-BACKBONE.md` status

## 3. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Per-schema outbox/inbox/DLQ DDL | ✓ |
| Flyway unique versions | ✓ (+ uniqueness test) |
| Transactional enqueue | ✓ InMemory |
| Polling relay started | ✓ platform |
| Inbox idempotent consume | ✓ choreography test |
| Meeting publishes via outbox | ✓ |
| Meeting→transcript occurrence remember | ✓ |
| Shared ops/transcript store graph | ✓ Primary beans |
| Rabbit topology name alignment | ✓ constants |
| JDBC stores | deferred |
| Spring AMQP adapter | deferred |

## 4. Testler

- `EventBackboneTest` + `MessagingResilienceScenarioTest` (20)
- `RabbitDlxTopologyTest`
- `MeetingTranscriptEventChoreographyTest`
- `FlywayMigrationUniquenessTest`

## 5. Bilinen riskler

- InMemory tek process; production JDBC + Rabbit sonraki altyapı adımı
- Legacy `outbox_messages` tabloları duruyor (kullanılmamalı)
- Relay poller daemon thread — graceful `EventBackbone.close()` gerekli

## 6. Sonraki faz

FAZ 11 — Model Management control plane / catalog wiring.
