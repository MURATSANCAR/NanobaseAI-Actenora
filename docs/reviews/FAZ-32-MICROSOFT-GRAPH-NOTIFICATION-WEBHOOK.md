# FAZ-32 Microsoft Graph Notification Webhook Binding

**Phase:** FAZ 32
**Date:** 2026-07-26
**Status:** Complete (validation handshake + clientState auth + idempotent dispatch)

## 1. Faz özeti

`microsoft-connection` bounded context'i abonelik yaşam döngüsünü ve change/lifecycle notification
idempotency'sini domain'de zaten taşıyordu, ancak platformda hiçbir HTTP girişi yoktu — Graph
gerçek push'ları alacak bir uç nokta bulamıyordu ("Real Graph subscription push" deferred item).
Bu turda platform, Graph'ın notification URL'ine karşılık gelen webhook'u bağladı:

- **Validation handshake:** Graph abonelik oluştururken `?validationToken=...` ile POST atar ve
  token'ın `text/plain` olarak 10 sn içinde geri yansıtılmasını bekler.
- **Notification delivery:** her item `clientState` (abonelik anında set edilen paylaşılan sır,
  kullanıcı JWT'si değil) ile doğrulanır ve `MicrosoftConnectionApi` üzerinden notification başına
  idempotency ile dispatch edilir.

## 2. Akış

```text
Graph → POST /api/v1/microsoft/webhooks/graph-notifications?validationToken=T
        ↓ (handshake)
        200 text/plain "T"

Graph → POST /api/v1/microsoft/webhooks/graph-notifications
        { "value": [ { subscriptionId, changeType|lifecycleEvent, clientState, resourceData … } ] }
        ↓ clientState == configured?  (hayır → rejected, atla)
        ↓ change  → onChangeNotification(claim: sub::changeType::resourceId)
        ↓ lifecycle → onLifecycleNotification(claim: sub::lifecycle::event)
        ↓ ilk kez → processed, tekrar → duplicates
        202 { received, processed, duplicates, rejected }
```

## 3. Değişenler

- `MicrosoftGraphWebhookController` (platform, `@ConditionalOnProperty(actenora.microsoft-graph.enabled=true)`)
- `PlatformSecurityConfiguration`: `/api/v1/microsoft/webhooks/**` permitAll (ENTRA chain)
- `TenantSecurityContextFilter`: webhook path'i JWT/mock header zorunluluğundan muaf
- `application.yml`: `actenora.microsoft-graph.enabled` + `webhook.client-state`
- OpenAPI: `microsoftGraphNotifications` path + `GraphNotificationBatch/Item/Result` şemaları
- `MicrosoftGraphWebhookBindingTest`

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Validation token echo (text/plain) | ✓ |
| clientState doğrulama (mismatch → reject) | ✓ |
| Change notification idempotent dispatch | ✓ |
| Lifecycle notification idempotent dispatch | ✓ |
| Boş/geçersiz batch → 400 | ✓ |
| JWT'siz erişim (shared-secret modeli) | ✓ |
| Downstream ingestion (transcript polling tetikleme) | deferred (hook boş) |
| Kalıcı NotificationInbox (JDBC) | deferred |

## 5. Testler

```bash
export JAVA_HOME=$PWD/.tools/jdk-21
./mvnw -pl apps/platform-backend,modules/microsoft-connection -am test \
  -Dtest='MicrosoftGraphWebhookBindingTest' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 6. Bilinen riskler

- Notification handler şu an boş (yalnızca claim/ack); gerçek transcript ingestion tetikleme bir
  sonraki fazda `onChangeNotification` handler'ına bağlanmalı (tercihen outbox üzerinden).
- `InMemoryNotificationInbox` process-local; kalıcı JDBC adapter aynı `claim` kontratını uygulamalı.
- Controller `MicrosoftConnectionApi` (.api) yanında `application.model` notification tiplerini
  kullanır; mevcut delivery webhook precedent'i ile aynı sınır esnekliğidir (ArchUnit api-external
  kuralı deposunda halihazırda bu precedent'i içeriyor).
- clientState tek paylaşılan sır; abonelik başına farklı clientState istenirse store'dan
  doğrulama gerekir.
```
