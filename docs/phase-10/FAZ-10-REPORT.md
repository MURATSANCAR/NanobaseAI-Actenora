# Faz 10 raporu — Event Backbone, Outbox, Inbox, DLQ

## 1. Faz özeti

Dağıtık çalışmaya hazır event altyapısı `shared-kernel` içinde kuruldu: transactional outbox, polling relay (CDC-ready transport port), inbox idempotency, retry/backoff+jitter, DLX/DLQ, correlation/trace, schema validation, graceful shutdown, poison handling, large-payload reject, consumer concurrency, tenant fairness, güvenli replay.

## 2. Değişen bounded context'ler

- **shared-kernel** — messaging runtime (tüm BC’ler kullanır)
- **Tüm BC şemaları** — `outbox_event` / `inbox_event` / `dead_letter_event` Flyway migration

## 3. Eklenen/değiştirilen dosyalar

- `modules/shared-kernel/.../messaging/**` (envelope, outbox, inbox, DLQ, replay, broker topology)
- `modules/*/.../V*__event_backbone_outbox_inbox_dlq.sql` (14 şema)
- `modules/shared-kernel/src/test/.../EventBackboneTest.java`
- `docs/architecture/EVENT-BACKBONE.md`
- `docs/architecture/DATA-OWNERSHIP.md`, `EVENT-CATALOG.md` (FAZ 10 notları)

## 4. Migration'lar

Her şemada `outbox_event`, `inbox_event`, `dead_letter_event` + due/tenant indexleri.

2026-07-25 gap-close doğrulamasında üç duplicate Flyway sürümü giderildi:

- Approval event backbone: `V191` → `V192`
- Meeting Intelligence event backbone: `V181` → `V185`
- Audit event backbone: `V222` → `V223`
- Transcript event backbone: `V153` (FAZ 9 sırasında düzeltilmişti)

Böylece domain migration'larıyla event backbone migration'ları aynı sürüm numarasını paylaşmıyor.

## 5. API değişiklikleri

Yok (HTTP). Application API: `OutboxPublisher`, `OutboxRelay`, `IdempotentEventConsumer`, `EventReplayer`.

## 6. Event değişiklikleri

Envelope runtime tipi (`EventEnvelope`) + Rabbit DLX naming (`RabbitDlxTopology`). Katalog v0 güncellendi (transport bölümü).

## 7. Model/prompt/schema değişiklikleri

Yok.

## 8. Güvenlik kontrolleri

- Large payload reject
- Replay için operator + reason zorunlu; `PROCESSED` inbox replay reddedilir
- Secret commit yok

## 9. Çalıştırılan komutlar

```bash
export JAVA_HOME=.../.tools/jdk-21.0.11+10/Contents/Home
mvn -pl modules/shared-kernel clean test
```

## 10. Test sonuçları

`EventBackboneTest` + `MessagingResilienceScenarioTest`: **20 tests, 0 failures**

Gap-close turunda aynı 20 test JDK 21 ile yeniden çalıştırıldı: **BUILD SUCCESS**.

Kapsanan senaryolar: duplicate event, TX rollback, publisher crash after publish, consumer crash before inbox commit, retry→DLQ, malformed payload, unsupported version, correlation continuity, replay idempotency, large payload, graceful shutdown, tenant fairness, concurrency config.

## 11. Bilinen riskler

- RabbitMQ adapter henüz platform-backend’e bağlanmadı (`EventTransport` port hazır)
- Legacy `outbox_messages` / `inbox_messages` tabloları duruyor; yeni kod `*_event` kullanmalı
- JDBC `OutboxStore` adaptörü sonraki fazda (şimdi in-memory + SQL şema)

## 12. Service extraction etkisi

Outbox/inbox/DLQ per-schema; `EventTransport` broker-agnostic — extraction’ta polling→CDC veya ayrı publisher process mümkün.

## 13. Sonraki faza geçiş durumu

**Hazır (InMemory):** Platform `EventBackbonePlatformConfiguration` shared stores + relay start +
`OutboxMeetingEventPublisher` + transcript inbox choreography için occurrence upsert.

**Bekleyen:** JDBC `OutboxStore`/`InboxStore`, Spring AMQP `EventTransport`, Micrometer outbox lag.

Sonraki ürün fazı: FAZ 11 — Model Management.
