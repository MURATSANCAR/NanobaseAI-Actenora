# FAZ-30 Delivery Provider Webhook Confirm

**Phase:** FAZ 30  
**Date:** 2026-07-25  
**Status:** Complete (provider webhook auto-confirm PROVIDER_ACCEPTED → DELIVERED)

## 1. Faz özeti

FAZ 23 manuel `confirm-delivered` HTTP’sini bağladı; provider callback yoktu. Bu turda shared-secret ile `POST /api/v1/delivery/webhooks/provider-delivered` eklendi. Acceptance hâlâ delivery sayılmaz; yalnızca explicit `delivered` event confirm eder. Webhook retry için zaten `DELIVERED` olan istek idempotent no-op.

## 2. Akış

```text
enqueue → drain → PROVIDER_ACCEPTED
        ↓
POST /api/v1/delivery/webhooks/provider-delivered
  Header: X-Actenora-Delivery-Webhook-Secret
  Body: { tenantId, deliveryRequestId, event: "delivered" }
        ↓
DELIVERED
```

JWT / mock identity gerekmez; tenant body’den gelir (provider callback).

## 3. Değişenler

- `DeliveryProviderWebhookController`
- `TenantSecurityContextFilter` + ENTRA `permitAll` webhook path
- `actenora.delivery.webhook.secret` config
- `confirmDelivered` already-DELIVERED idempotent
- OpenAPI webhook path/schemas
- Binding + dispatcher tests

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Provider webhook HTTP | ✓ |
| Shared-secret auth | ✓ |
| No user JWT required | ✓ |
| Only delivered event | ✓ |
| PROVIDER_ACCEPTED → DELIVERED | ✓ |
| Webhook retry idempotent | ✓ |
| Lookup by providerMessageId alone | done (FAZ 31) |
| Real Graph subscription push | deferred |
| JDBC delivery repos | deferred |

## 5. Testler

```bash
export JAVA_HOME=$PWD/.tools/jdk-21
./mvnw -pl apps/platform-backend,modules/delivery -am test \
  -Dtest='DeliveryAuthBindingTest,DeliveryDispatcherServiceTest' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 6. Bilinen riskler

- Local default secret; production `ACTENORA_DELIVERY_WEBHOOK_SECRET` zorunlu olmalı.
- ProviderMessageId henüz lookup anahtarı değil; callback deliveryRequestId bilmeli.
- TenantId webhook body’de — secret sızıntısında cross-tenant confirm riski (secret rotation + IP allowlist deferred).
