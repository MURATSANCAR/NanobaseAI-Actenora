# FAZ-23 Delivery confirmDelivered Binding

**Phase:** FAZ 23  
**Date:** 2026-07-25  
**Status:** Complete (PROVIDER_ACCEPTED → DELIVERED via auth HTTP)

## 1. Faz özeti

FAZ 20 drain MailHog capture ile `PROVIDER_ACCEPTED` üretiyordu ama final `DELIVERED` yalnızca domain’de vardı (`confirmDelivered`). Bu turda auth-bound HTTP eklendi: acceptance ≠ delivery invariant korunuyor.

## 2. Akış

```text
enqueue → drain → PROVIDER_ACCEPTED
        ↓
POST /api/v1/delivery/requests/{requestId}/confirm-delivered
        ↓
DELIVERED
```

QUEUED / diğer status → `INVALID_STATUS` (409).

## 3. Bu turda değişenler

- `DeliveryAuthController.confirmDelivered`
- Exception mapping: `INVALID_STATUS` → 409, `DELIVERY_NOT_FOUND` → 404
- `DeliveryAuthBindingTest` — happy path + queued reject
- OpenAPI confirm-delivered path

## 4. Kabul matrisi

| Gereksinim | Durum |
|------------|--------|
| confirmDelivered HTTP | ✓ |
| Only PROVIDER_ACCEPTED → DELIVERED | ✓ |
| DELIVERY_MANAGE | ✓ |
| OpenAPI | ✓ |
| Provider webhook auto-confirm | deferred |
| JDBC delivery repos | deferred |

## 5. Testler

```bash
export JAVA_HOME=$PWD/.tools/jdk-21
./mvnw -pl apps/platform-backend test -Dtest='DeliveryAuthBindingTest'
./mvnw -pl modules/delivery test -Dtest='DeliveryDispatcherServiceTest'
```

## 6. Bilinen riskler

- Confirm hâlâ manuel HTTP; gerçek provider webhook yok.
- Drain global; tenant filter yok (FAZ 20 riski devam).
