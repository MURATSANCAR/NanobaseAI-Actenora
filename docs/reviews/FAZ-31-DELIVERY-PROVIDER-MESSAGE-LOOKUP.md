# FAZ-31 Delivery Provider Message Id Lookup

**Phase:** FAZ 31  
**Date:** 2026-07-26  
**Status:** Complete (webhook can confirm via providerMessageId alone)

## 1. Faz özeti

FAZ 30 webhook callback’inde `deliveryRequestId` zorunluydu; gerçek provider’lar genelde yalnızca kendi message id’lerini gönderir. Bu turda tenant-scoped `providerMessageId` index’i eklendi; webhook `deliveryRequestId` veya `providerMessageId` ile resolve edip confirm eder.

## 2. Akış

```text
drain → PROVIDER_ACCEPTED (+ providerMessageId indexed)
        ↓
POST /webhooks/provider-delivered
  { tenantId, providerMessageId }   // deliveryRequestId optional
        ↓
resolveByProviderMessageId → confirmDelivered
        ↓
DELIVERED
```

## 3. Değişenler

- `DeliveryRequestRepository.findByProviderMessageId`
- InMemory provider-message index on save
- `DeliveryApi.resolveByProviderMessageId`
- Webhook payload: request id **or** provider message id
- OpenAPI schema update
- Binding + dispatcher tests

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| Lookup by providerMessageId | ✓ |
| Tenant-scoped index | ✓ |
| Webhook without deliveryRequestId | ✓ |
| Response returns resolved request id | ✓ |
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

- Index InMemory; JDBC adapter aynı contract’ı uygulamalı.
- Aynı providerMessageId’nin tenant içinde yeniden kullanımı overwrite eder (provider uniqueness varsayımı).
