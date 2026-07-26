# FAZ-20 Delivery Enqueue / Worker Binding

**Phase:** FAZ 20  
**Date:** 2026-07-25  
**Status:** Complete (auth enqueue + in-process drain; MailHog capture-only)

## 1. Faz özeti

FAZ 19 READY order ürettikten sonra recipient enqueue ve worker drain HTTP’de yoktu; domain (`DeliveryDispatcherService`, `DeliveryWorker`, MailHog capture) zaten vardı. Bu turda READY order üzerinden explicit enqueue, request status ve tek batch drain platform’a bağlandı. Gerçek SMTP socket / Graph / ayrı `services/delivery-worker` process bilerek deferred.

## 2. Akış

```text
POST /delivery/orders                         → READY (FAZ 19)
        ↓
POST /delivery/orders/{orderId}/enqueue
     { recipients[], subject?, bodyText? }
        ↓
DeliveryApi.enqueue (approval gate + idempotent recipients) → QUEUED
        ↓
POST /delivery/drain  → DeliveryWorker.pollOnce → MailHog capture (PROVIDER_ACCEPTED)
        ↓
GET /delivery/requests/{requestId} → status
```

`confirmDelivered` (PROVIDER_ACCEPTED ≠ DELIVERED) hâlâ manuel/domain; HTTP’ye alınmadı.

## 3. Bu turda değişenler

- `DeliveryAuthController` — enqueue / request status / drain (+ DeliveryWorker inject)
- `DeliveryAuthBindingTest` — full adapter + MailHog; enqueue→duplicate→drain capture
- OpenAPI — enqueue/request/drain schemas

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Enqueue only from READY order | ✓ |
| Recipients from request body | ✓ |
| Policy defaults (MailHog) | ✓ |
| Idempotent recipient suppress | ✓ |
| Drain via worker pollOnce | ✓ |
| MailHog capture (not SMTP) | ✓ |
| Status GET | ✓ |
| Permission DELIVERY_MANAGE | ✓ |
| confirmDelivered HTTP | deferred |
| Real SMTP / Graph | deferred |
| Separate worker process | deferred |
| Order READY→IN_FLIGHT | deferred |

## 5. Testler

- `DeliveryAuthBindingTest` (5): order idempotency; enqueue+duplicate+drain+MailHog; pending block; permission; foreign tenant
- Module: `DeliveryDispatcherServiceTest`, `ExternalDeliveryGateTest`

```bash
export JAVA_HOME=$PWD/.tools/jdk-21
./mvnw -pl modules/delivery test
./mvnw -pl apps/platform-backend test -Dtest='DeliveryAuthBindingTest'
```

## 6. Bilinen riskler

- Drain global poll; tenant filter yok (InMemory shared queue — OK for local MVP).
- PROVIDER_ACCEPTED final DELIVERED sayılmaz; ürün UI bunu ayırmalı.
- Recipient roster meeting’den otomatik çekilmiyor.
